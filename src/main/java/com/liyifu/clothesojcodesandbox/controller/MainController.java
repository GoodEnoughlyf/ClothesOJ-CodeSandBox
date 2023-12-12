package com.liyifu.clothesojcodesandbox.controller;

import com.liyifu.clothesojcodesandbox.JavaDockerCodeSandBox;
import com.liyifu.clothesojcodesandbox.JavaNativeCodeSandBox;
import com.liyifu.clothesojcodesandbox.model.ExecuteCodeRequest;
import com.liyifu.clothesojcodesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/")
public class MainController {

    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";

    private static final String AUTH_REQUEST_SECRET = "secretKey";

    //用docker
    @Resource
    private JavaDockerCodeSandBox javaDockerCodeSandBox;

    @Resource
    private JavaNativeCodeSandBox javaNativeCodeSandBox;

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request, HttpServletResponse response) {
        // 基本的认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
//        return javaDockerCodeSandBox.executeCode(executeCodeRequest);
        return javaNativeCodeSandBox.executeCode(executeCodeRequest);
    }


}
