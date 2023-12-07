package com.liyifu.clothesojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.liyifu.clothesojcodesandbox.model.ExecuteCodeRequest;
import com.liyifu.clothesojcodesandbox.model.ExecuteCodeResponse;
import com.liyifu.clothesojcodesandbox.model.ExecuteMessage;
import com.liyifu.clothesojcodesandbox.model.JudgeInfo;
import com.liyifu.clothesojcodesandbox.utils.ProcessUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 本地代码沙箱  （本地不安全，该类作为测试）
 */
public class JavaNativeCodeSandBox implements CodeSandBox{
    //存放用户代码的文件夹
    private static final String GLOBAL_CODE_DIR_NAME="tempCode";
    //Java文件统一类名
    private static final String GLOBAL_JAVA_CLASS_NAME="Main.java";
    //防止一直休眠  给个中断时间
    private static final Long TIME_OUT=5000L;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        List<String> inputList = executeCodeRequest.getInputList();
        //1、将用户代码保存为文件
        String userDir = System.getProperty("user.dir"); //拿到根目录，也就是ClothesOJ-CodeSandBox项目目录
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME; //拿到存放代码文件夹的全局路径
            //建立一个存放用户提交代码的文件夹，没有则新建
        if(!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
            //将用户提交的代码进行隔离，也就是每次提交的代码都新建一个文件夹保存
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID(); //用uuid区分每一个文件夹
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME; //代码文件
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        //2、编译代码，得到class文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsoluteFile());
            //Runtime.getRuntime().exec 用于调用外部可执行程序或系统命令   就是创建一个本机进程，并返回 Process 子类的一个实例，该实例可用来控制进程并获取相关信息。
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        //3、执行代码，得到输出结果
            //有多组实例
        List<ExecuteMessage> executeMessageList=new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd=String.format("java -cp %s Main %s",userCodeParentPath,inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                //超时控制   todo 了解多线程编程
                new Thread(()->{
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("程序运行超时，已经中断");
                        //销毁超时进程
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtil.runProcessAndGetMessage(runProcess, "运行");
                System.out.println(executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }
        }

        //4、收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList=new ArrayList<>();
            //获取每个用例的最大时间，用于判断是否超时
        long maxTime=0L;
        for (ExecuteMessage executeMessage : executeMessageList) {
            //如果运行有一组实例运行报错，则直接跳出循环
            String errMessage = executeMessage.getErrMessage();
            if(StrUtil.isNotEmpty(errMessage)){
                executeCodeResponse.setStatus(3); //状态码为3 表示实例执行失败
                break;
            }
            //实例全部执行成功，则返回每一组实例的结果
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if(time!=null){
                maxTime=Math.max(time,maxTime);
            }
        }
        //如果返回的结果数量能和每一个实例的执行信息数量能对应上，那么状态码为1 表示成功
        if(outputList.size()==executeMessageList.size()){
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        //5、文件清理
        if(userCodeFile.getParentFile()!=null){
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除"+(del?"成功":"失败"));
        }

        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     * @param e
     * @return
     */
    // 6、错误处理  提升程序健壮性
    private ExecuteCodeResponse getErrorResponse(Throwable e){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //代码沙箱错误 返回2
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    /**
     * 测试 java本地代码沙箱    (测试ok后，后续实现doker容器内构建代码沙箱，进行远程调用)
     * @param args
     */
    public static void main(String[] args) {
        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2","3 4"));

        //1、测试正常情况下调用本地代码沙箱
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);
//        System.out.println(code);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }
}


