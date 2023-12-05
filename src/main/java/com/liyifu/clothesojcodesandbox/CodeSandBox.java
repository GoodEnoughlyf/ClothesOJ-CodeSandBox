package com.liyifu.clothesojcodesandbox;


import com.liyifu.clothesojcodesandbox.model.ExecuteCodeRequest;
import com.liyifu.clothesojcodesandbox.model.ExecuteCodeResponse;

/**
 * 代码沙箱接口定义
 */
public interface CodeSandBox {
    /**
     * 代码沙箱执行代码接口
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
