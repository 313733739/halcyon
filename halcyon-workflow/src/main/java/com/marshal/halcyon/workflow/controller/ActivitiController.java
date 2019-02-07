package com.marshal.halcyon.workflow.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.marshal.halcyon.core.component.ResponseData;
import com.marshal.halcyon.core.util.ResponseUtils;
import com.marshal.halcyon.workflow.service.ActivitiService;
import io.swagger.annotations.ApiParam;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.ActivitiIllegalArgumentException;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.impl.util.io.InputStreamSource;
import org.activiti.engine.repository.Model;
import org.activiti.rest.common.api.DataResponse;
import org.activiti.rest.service.api.RestResponseFactory;
import org.activiti.rest.service.api.repository.ModelRequest;
import org.activiti.rest.service.api.repository.ModelResponse;
import org.activiti.rest.service.api.repository.ProcessDefinitionResponse;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @auth: Marshal
 * @date: 2018/10/29
 * @desc: activiti工作流controller
 */
@RestController
@RequestMapping("/workflow")
public class ActivitiController {

    private Logger logger = LoggerFactory.getLogger(ActivitiController.class);

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RepositoryService repositoryService;

    @Autowired
    RestResponseFactory restResponseFactory;

    @Autowired
    ActivitiService activitiService;

    /**
     * 新建模型(editor)
     *
     * @param modelRequest
     * @param request
     * @param response
     * @return
     */
    @RequestMapping(value = "/repository/models", method = RequestMethod.POST, produces = "application/json")
    public ResponseData createModel(@RequestBody ModelRequest modelRequest, HttpServletRequest request,
                                    HttpServletResponse response) {
        Model model = repositoryService.newModel();
        model.setCategory(modelRequest.getCategory());
        model.setDeploymentId(modelRequest.getDeploymentId());
        model.setKey(modelRequest.getKey());
        model.setMetaInfo(modelRequest.getMetaInfo());
        model.setName(modelRequest.getName());
        model.setVersion(modelRequest.getVersion());
        model.setTenantId(modelRequest.getTenantId());

        repositoryService.saveModel(model);
        response.setStatus(HttpStatus.CREATED.value());

        HashMap<String, Object> content = new HashMap<>();
        content.put("resourceId", model.getId());

        HashMap<String, String> properties = new HashMap<>();
        properties.put("process_id", modelRequest.getKey());
        properties.put("name", modelRequest.getName());
        properties.put("process_namespace", modelRequest.getCategory());
        content.put("properties", properties);

        HashMap<String, String> stencilset = new HashMap<>();
        stencilset.put("namespace", "http://b3mn.org/stencilset/bpmn2.0#");
        content.put("stencilset", stencilset);

        try {
            repositoryService.addModelEditorSource(model.getId(), objectMapper.writeValueAsBytes(content));
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage(), e);
        }

        restResponseFactory.createModelResponse(model);
        return ResponseUtils.responseOk("创建模型成功！");
    }

    /**
     * 删除流程模型
     *
     * @param modelId
     * @return
     */
    @RequestMapping("/repository/model/{modelId}/delete")
    public ResponseData modelDelete(@PathVariable String modelId) {
        try {
            repositoryService.deleteModel(modelId);
        } catch (Exception e) {
            ResponseUtils.responseErr("删除失败!");
        }
        return ResponseUtils.responseOk("删除成功!");
    }

    /**
     * 部署流程模型
     *
     * @param modelId
     * @param request
     * @return
     */
    @RequestMapping("/repository/model/{modelId}/deploy")
    @ResponseBody
    public ResponseData modelDeployment(@PathVariable String modelId, HttpServletRequest request) {
        Model model = null;
        try {
            model = activitiService.deployModel(modelId);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            ResponseData rd = new ResponseData(false, "");
            rd.setMessage(e.getMessage());
            return rd;
        }
        model.setName(model.getName());
        ResponseData responseData = new ResponseData(Collections.singletonList(model));
        responseData.setMessage("部署成功");
        return responseData;
    }


    /**
     * 导出流程文件
     *
     * @param modelId
     * @param type
     * @return
     * @throws IOException
     */
    @RequestMapping(value = "/repository/model/{modelId}/export", produces = "text/xml")
    public ResponseEntity<byte[]> modelExport(@PathVariable String modelId,
                                              @RequestParam(defaultValue = "") String type) throws IOException {
        Model model = repositoryService.getModel(modelId);
        byte[] modelData = repositoryService.getModelEditorSource(modelId);
        JsonNode jsonNode = objectMapper.readTree(modelData);
        BpmnModel bpmnModel = new BpmnJsonConverter().convertToBpmnModel(jsonNode);
        byte[] xmlBytes = new BpmnXMLConverter().convertToXML(bpmnModel, "UTF-8");
        HttpHeaders responseHeaders = new HttpHeaders();
        String id = model.getId();
        if (bpmnModel.getMainProcess() != null) {
            id = bpmnModel.getMainProcess().getId();
        }
        if ("bpmn20".equalsIgnoreCase(type)) {
            responseHeaders.set("Content-Disposition", "attachment;filename=" + id + ".bpmn20.xml");
            responseHeaders.set("Content-Type", "application/octet-stream");
        } else {
            responseHeaders.set("Content-Type", "text/xml;charset=utf8");
        }
        try {
            return new ResponseEntity<>(xmlBytes, responseHeaders, HttpStatus.OK);
        } catch (Exception e) {
            throw new ActivitiIllegalArgumentException("Error exporting diagram", e);
        }
    }

    /**
     * 导入流程文件
     *
     * @param request
     * @return
     * @throws FileUploadException
     * @throws IOException
     */
    @RequestMapping(value = "/repository/model/import", produces = "text/html;charset=UTF-8")
    public String importModel(HttpServletRequest request) throws FileUploadException, IOException {
        if (!ServletFileUpload.isMultipartContent(request)) {
            return "<script>alert('NOT a Multipart Request')</script>";
        }
        InputStream fileInputStream = null;
        String name = null;
        String key = null;
        String category = null;
        if (request instanceof MultipartHttpServletRequest) {
            MultipartFile file = ((MultipartHttpServletRequest) request).getFile("file");
            if (file != null) {
                fileInputStream = file.getInputStream();
            }
            name = request.getParameter("name");
            key = request.getParameter("key");
            category = request.getParameter("category");
        } else {
            ServletFileUpload upload = new ServletFileUpload(new DiskFileItemFactory());
            List<FileItem> items = upload.parseRequest(request);
            FileItem item = null;
            Map<String, String> parameters = new HashMap<>();
            for (FileItem fi : items) {
                if (!fi.isFormField()) {
                    item = fi;
                } else {
                    parameters.put(fi.getFieldName(), fi.getString("UTF-8"));
                }
            }
            if (item != null && StringUtils.isNotEmpty(item.getName())) {
                fileInputStream = item.getInputStream();
                name = parameters.get("name");
                key = parameters.get("key");
                category = parameters.get("category");
            }
        }
        if (fileInputStream == null) {
            return "<script>alert('File Content is Null')</script>";
        }
        try (InputStream inputStream = fileInputStream) {
            InputStreamSource source = new InputStreamSource(fileInputStream);
            BpmnModel bpmnModel = new BpmnXMLConverter().convertToBpmnModel(source, false, false, "UTF-8");
            bpmnModel.getMainProcess().setId(StringUtils.defaultIfEmpty(key, bpmnModel.getMainProcess().getId()));
            bpmnModel.getMainProcess().setName(StringUtils.defaultIfEmpty(name, bpmnModel.getMainProcess().getName()));

            Model model = repositoryService.newModel();
            model.setCategory(StringUtils.defaultIfEmpty(category, "default"));
            model.setDeploymentId(null);
            model.setKey(bpmnModel.getMainProcess().getId());
            model.setName(bpmnModel.getMainProcess().getName());

            String metaInfo = model.getMetaInfo();
            if (StringUtils.isEmpty(metaInfo)) {
                HashMap<String, String> map = new HashMap<>();
                map.put("name", model.getName());
                map.put("version", String.valueOf(model.getVersion()));
                metaInfo = objectMapper.writeValueAsString(map);
            }
            model.setMetaInfo(metaInfo);

            repositoryService.saveModel(model);

            ObjectNode content = new BpmnJsonConverter().convertToJson(bpmnModel);
//            content = approvalRule.processCustomProperties(content);

            repositoryService.addModelEditorSource(model.getId(), objectMapper.writeValueAsBytes(content));
            restResponseFactory.createModelResponse(model);
            return "<script>window.parent.onImportComplete(true)</script>";
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return "<script>window.parent.onImportComplete(false,'请上传符合 BPMN 规范的文件')</script>";
        }
    }

}
