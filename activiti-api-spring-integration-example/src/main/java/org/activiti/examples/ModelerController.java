package org.activiti.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/models/")
public class ModelerController {

    @Autowired
    private SecurityUtil securityUtil;

    @Autowired
    RepositoryService repositoryService;

    @Autowired
    ProcessEngine processEngine;
    @Autowired
    ObjectMapper objectMapper;

    /**
     * 获取所有模型
     * @return
     */
    @GetMapping
    @RequestMapping("modelist")
    public String modelList(org.springframework.ui.Model model){
        securityUtil.logInAs("system");
        List<Model> models = repositoryService.createModelQuery().orderByCreateTime().desc().list();
        model.addAttribute("models",models);
        return "model/list";
    }

    /**
     * 新建一个空模型
     * @return
     * @throws UnsupportedEncodingException
     */
    @PostMapping("newmodel")
    public Object newModel(NewModel newModel) throws UnsupportedEncodingException {
        //securityUtil.logInAs("admin");
        //初始化一个空模型
        Model model = repositoryService.newModel();

        //设置一些默认信息
        String name = newModel.getName();
        String description = newModel.getDes();
        int revision = 1;
        String key = newModel.getKey();

        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(ModelDataJsonConstants.MODEL_NAME, name);
        modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, description);
        modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);

        model.setName(name);
        model.setKey(key);
        model.setMetaInfo(modelNode.toString());

        repositoryService.saveModel(model);
        String id = model.getId();

        //完善ModelEditorSource
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");
        ObjectNode stencilSetNode = objectMapper.createObjectNode();
        stencilSetNode.put("namespace",
                "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.put("stencilset", stencilSetNode);
        repositoryService.addModelEditorSource(id,editorNode.toString().getBytes("utf-8"));
        return "redirect:/static/modeler.html?modelId="+id;
    }



    /**
     * 删除模型
     * @param id
     * @return
     */
    @DeleteMapping("del/{id}")
    @ResponseBody
    public String deleteModel(@PathVariable("id")String id){
        repositoryService.deleteModel(id);
        return "ok";
    }



    /**
     * 发布模型为流程定义
     * @param id
     * @return
     * @throws Exception
     */
    @PostMapping("{id}/deployment")
    @ResponseBody
    public String deploy(@PathVariable("id")String id) throws Exception {

        Map<String,Object> map = new HashMap<>();

        //获取模型
        Model modelData = repositoryService.getModel(id);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

        if (bytes == null) {
            return "模型数据为空，请先设计流程并成功保存，再进行发布。";
        }

        JsonNode modelNode = new ObjectMapper().readTree(bytes);

        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if(model.getProcesses().size()==0){
            return "数据模型不符要求，请至少设计一条主线流程。";
            /*return ToWeb.buildResult().status(Status.FAIL)
                    .msg("数据模型不符要求，请至少设计一条主线流程。");*/
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, "UTF-8"))
                .deploy();
        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);

        return "ok";
    }

}
