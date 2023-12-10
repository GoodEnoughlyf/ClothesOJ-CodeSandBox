package com.liyifu.clothesojcodesandbox.model;

import lombok.Data;

/**
 * 进程执行信息
 */
@Data
public class ExecuteMessage {
    private Integer exitValue;

    private String message;

    private String errMessage;

    /**
     * 用时
     */
    private Long time;

    private Long memory;

}
