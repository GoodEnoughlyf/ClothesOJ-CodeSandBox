package com.liyifu.clothesojcodesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.liyifu.clothesojcodesandbox.CodeSandBox;
import com.liyifu.clothesojcodesandbox.model.ExecuteCodeRequest;
import com.liyifu.clothesojcodesandbox.model.ExecuteCodeResponse;
import com.liyifu.clothesojcodesandbox.model.ExecuteMessage;
import com.liyifu.clothesojcodesandbox.model.JudgeInfo;
import com.liyifu.clothesojcodesandbox.utils.ProcessUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class JavaDockerCodeSandBox implements CodeSandBox {

    //存放用户代码的文件夹
    private static final String GLOBAL_CODE_DIR_NAME = "tempCode";
    //java类名
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    //防止一直休眠  给个中断时间
    private static final Long TIME_OUT = 5000L;
    //是否有该镜像
    private static final Boolean FIRST_INIT = true;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        // 1、将用户代码保存为文件
        //建立一个存放用户提交代码的文件夹，没有则新建
        String userDir = System.getProperty("user.dir");  //拿到根目录
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;   //拿到文件夹的全局路径
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        //将用户提交的代码隔离存放  （也就是一个代码一个文件夹）
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();    //用户代码存放父级文件夹
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;   //用户代码存放文件
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2、编译代码，得到 class文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println(executeMessage);
        } catch (Exception e) {
            return getErrorResponse(e);
        }

        // 3、创建容器，上传编译文件
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 3.1、拉取镜像
        String image = "openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback resultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("拉取镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(resultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("镜像拉取完成");
        // 3.2、创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        HostConfig hostConfig = new HostConfig();
        // 限制内存
        hostConfig.withMemory(100 * 1000 * 1000L);
        // 设置CPU
        hostConfig.withCpuCount(1L);
        // 设置容器挂载目录
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                .withAttachStderr(true) // 开启输入输出
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true) // 开启一个交互终端
                .exec();
        String containerId = createContainerResponse.getId();
        System.out.println("创建容器id：" + containerId);

        //4、启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // 5、执行命令 docker exec containtId java -cp /app Main 1 2
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true) // 开启输入输出
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse.getId());

            ExecuteMessage execDockerMessage = new ExecuteMessage();
            final String[] messageDocker = {null};
            final String[] errorDockerMessage = {null};
            long time = 0L;
            String execId = execCreateCmdResponse.getId();
            // 判断超时变量
            final boolean[] isTimeOut = {true};
            if (execId == null) {
                throw new RuntimeException("执行命令不存在");
            }
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {

                @Override
                public void onComplete() {
                    // 执行完成，设置为 false 不超时
                    isTimeOut[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    // 获取程序执行信息
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorDockerMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errorDockerMessage[0]);
                    } else {
                        messageDocker[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + messageDocker[0]);
                    }
                    super.onNext(frame);
                }
            };

            // 获取占用的内存
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    Long usageMemory = statistics.getMemoryStats().getUsage();
                    System.out.println("内存占用：" + usageMemory);
                    maxMemory[0] = Math.max(usageMemory, maxMemory[0]);
                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });
            statsCmd.exec(statisticsResultCallback);
            try {
                // 执行启动命令
                // 开始前获取时间
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion();
                // 结束计时
                stopWatch.stop();
                // 获取总共时间
                time = stopWatch.getLastTaskTimeMillis();
                // 关闭统计
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            System.out.println("耗时：" + time + " ms");
            execDockerMessage.setMessage(messageDocker[0]);
            execDockerMessage.setErrMessage(errorDockerMessage[0]);
            execDockerMessage.setTime(time);
            execDockerMessage.setMemory(maxMemory[0]);
            executeMessageList.add(execDockerMessage);
        }

        // 6、收集整理输出结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 根据每个用例的时间得到最大时间，判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errMessage = executeMessage.getErrMessage();
            if (StrUtil.isNotEmpty(errMessage)) {
                executeCodeResponse.setMessage(errMessage);
                //用户提交代码错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time2 = executeMessage.getTime();
            if (time2 != null) {
                maxTime = Math.max(maxTime, time2);
            }
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
//        judgeInfo.setMemory();   太麻烦了，暂不实现
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 7、文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }

        return executeCodeResponse;
    }

    /**
     * 获取错误响应
     *
     * @param e
     * @return
     */
    // 8、错误处理  提升程序健壮性
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }


    //测试 main函数
    public static void main(String[] args) {
        JavaDockerCodeSandBox javaDockerCodeSandbox = new JavaDockerCodeSandBox();
//        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "3 4"));
        String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.java", StandardCharsets.UTF_8);   //hutool工具包中可以读取resource文件内容

        //测试1
//        String code= ResourceUtil.readStr("testCode/simpleCompute/Main.java",StandardCharsets.UTF_8);   //hutool工具包中可以读取resource文件内容
        //测试2   （一直睡眠，占用资源）
//        String code = ResourceUtil.readStr("testCode/unsafeCode/SleepError.java", StandardCharsets.UTF_8);   //hutool工具包中可以读取resource文件内容
        //测试3    （无限占用空间 （浪费系统内存））
//        String code = ResourceUtil.readStr("testCode/unsafeCode/MemoryError.java", StandardCharsets.UTF_8);   //hutool工具包中可以读取resource文件内容
        //测试4   （向服务器读文件，文件信息泄漏）
//        String code = ResourceUtil.readStr("testCode/unsafeCode/ReadFileError.java", StandardCharsets.UTF_8);   //hutool工具包中可以读取resource文件内容
        //测试5     （向服务器写文件 ，写入木马）
//        String code = ResourceUtil.readStr("testCode/unsafeCode/WirteFileError.java", StandardCharsets.UTF_8);   //hutool工具包中可以读取resource文件内容
        //测试6   （运行程序  ，刚执行的木马）
//        String code = ResourceUtil.readStr("testCode/unsafeCode/RunFileError.java", StandardCharsets.UTF_8);   //hutool工具包中可以读取resource文件内容

        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaDockerCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }
}
