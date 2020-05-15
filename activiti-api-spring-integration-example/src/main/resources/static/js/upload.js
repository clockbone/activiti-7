(function (window){

    "use strict";

    var $ = window.$,
        g_ = {};

    var frameId, formId, tempform, ajaxOptions, ajaxXml, ajaxDone = false;

    /**
     * @param options
     *  {
     *      url: "",
     *      timeout: "",
     *      files: [],
     *      data: {},
     *      success: function({
     *          responseText: "",
     *          responseJSON: "",
     *          status: 0|200|400|404|...,
     *          statusText: ""
     *      }),
     *      complete: function()
     *  }
     */
    g_.upload = function (options) {
        var body = $('body');
        var id = new Date().getTime();
        frameId = 'jUploadFrame' + id;
        formId = 'jUploadForm' + id;
        /* 插入IFrame */
        var iframe = $('<iframe id="' + frameId + '" name="' + frameId + '"></iframe>');
        iframe.hide();
        $('#' + frameId)[0] || body.append(iframe);

        tempform = $('<form enctype="multipart/form-data" method="post"' + ' target="' + frameId + '" action="' + (options.url || '') + '"></form>');
        tempform.hide();
        /* 文件元素导入 */
        var originalFile = $('<input type="file" name="files">');
        $.each(options.files, function (index, file) {
            var cloneElement = $(originalFile).clone();
            cloneElement[0].files = file;  //FileList
            $(cloneElement).appendTo(tempform);
        });
        /* 数据元素导入 */
        var originalElement = $('<input type="hidden" name="" value="">');
        $.each(options.data, function (name, value) {
            var cloneElement = originalElement.clone();
            cloneElement.attr({'name': name, 'value': value});
            $(cloneElement).appendTo(tempform);
        });
        /* 编码 */
        $(document.body).append(tempform);

        /* 发送请求 */
        ajaxOptions = $.extend({}, $.ajaxSettings, options);
        if (ajaxOptions.global && !$.active++) {
            $.event.trigger("ajaxStart");
        }
        ajaxXml = {
            getAllResponseHeaders: function () {
                return "";
            }
        };
        if (ajaxOptions.global) {
            $.event.trigger("ajaxSend", [ajaxXml, ajaxOptions]);
        }

        if (ajaxOptions.timeout > 0) {
            setTimeout(function () {
                if (!ajaxDone) _uploadCallback("timeout");
            }, ajaxOptions.timeout);
        }

        $(tempform).submit();

        if (window.attachEvent) {
            document.getElementById(frameId).attachEvent('onload', _uploadCallback);
        } else {
            document.getElementById(frameId).addEventListener('load', _uploadCallback, false);
        }
    };

    /**
     * Ajax请求回调
     * @param isTimeout
     * @private
     */
    function _uploadCallback(isTimeout) {
        var io = document.getElementById(frameId);
        try {
            if (io.contentWindow) {
                ajaxXml.responseText = io.contentWindow.document.body ? io.contentWindow.document.body.innerHTML : null;

            } else if (io.contentDocument) {
                ajaxXml.responseText = io.contentDocument.document.body ? io.contentDocument.document.body.innerHTML : null;
            }
        } catch (e) {
            ajaxXml.status = 500;
            ajaxXml.statusText = "Internal Server Error";
        }

        ajaxDone = true;

        try {
            if (isTimeout != "timeout") {
                ajaxXml.status = 200;
                ajaxXml.statusText = "OK";
                ajaxXml.responseJSON = uploadHttpData(ajaxXml);
                if (ajaxOptions.success) {
                    ajaxOptions.success(ajaxXml.responseJSON, ajaxXml);
                }

                if (ajaxOptions.global) {
                    $.event.trigger("ajaxSuccess", [ajaxXml, ajaxOptions]);
                }
            } else {
                ajaxXml.status = 504;
                ajaxXml.statusText = "Gateway Timeout";
            }
        } catch (e) {
            ajaxXml.status = 500;
            ajaxXml.statusText = "Internal Server Error";
        }

        if (ajaxOptions.global) {
            $.event.trigger("ajaxComplete", [ajaxXml, ajaxOptions]);
        }

        if (ajaxOptions.global && !--$.active) {
            $.event.trigger("ajaxStop");
        }

        if (ajaxOptions.complete) {
            ajaxOptions.complete(ajaxXml);
        }

        $(io).unbind();

        setTimeout(function () {
            $(io).remove();
            $(tempform).remove();
        }, 100);

        ajaxXml = null;
    }

    /**
     * 发送请求
     * @param ajaXml
     * @returns Data
     */
    function uploadHttpData(ajaXml) {
        var data = ajaXml.responseText;
        data = data.replace(/<\/?.+?>/g, "");
        eval("data = " + data);
        return data;
    }

    window.$up = g_;

})(this);