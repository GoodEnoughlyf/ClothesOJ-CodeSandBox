package com.liyifu.clothesojcodesandbox.utils;

import com.liyifu.clothesojcodesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessUtil {
    /**
     * 执行进程  并获取信息
     * @param runProcess
     * @param optionName 判断是编译还是运行
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process runProcess,String optionName){
        ExecuteMessage executeMessage = new ExecuteMessage();

        try {
            //判断每个用例耗时
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            //Process进程执行完，会返回一个退出码，要么为正确退出，要么为错误退出
            int exitValue = runProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            //正确退出
            if(exitValue==0){
                System.out.println(optionName+"成功");
                //通过Process进程获取正确输出到控制台信息
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                //逐行读取
                String compileOutputLine;
                while((compileOutputLine= bufferedReader.readLine())!=null){
//                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                System.out.println("hhhhh"+compileOutputStringBuilder);
                executeMessage.setMessage(compileOutputStringBuilder.toString());
            }else { //异常退出
                System.out.println(optionName+"失败，错误码："+exitValue);
                //通过Process进程获取正常输出到控制台信息
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                //逐行读取
                String compileOutputLine;
                while((compileOutputLine= bufferedReader.readLine())!=null){
//                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                    compileOutputStringBuilder.append(compileOutputLine);
                }
                System.out.println("hhhhh"+compileOutputStringBuilder);
                executeMessage.setMessage(compileOutputStringBuilder.toString());

                //分批获取进程的错误输出
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(runProcess.getErrorStream()));
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();
                //逐行读取
                String errorCompileOutputLine;
                while((errorCompileOutputLine= errorBufferedReader.readLine())!=null){
                    compileOutputStringBuilder.append(errorCompileOutputStringBuilder).append("\n");
                }
                executeMessage.setErrMessage(errorCompileOutputStringBuilder.toString());
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        return executeMessage;
    }
}
