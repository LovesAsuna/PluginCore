package org.sct.easylib.util.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.sct.easylib.EasyLib;
import org.sct.easylib.EasyLibAPI;
import org.sct.easylib.data.DependenceData;
import org.sct.easylib.data.LibData;
import org.sct.easylib.util.BasicUtil;
import org.sct.easylib.util.function.stack.StackTrace;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author LovesAsuna
 * @date 2020/5/2 14:59
 */

public class Dependence {
    @Getter
    private final String MD5;
    @Getter
    private final String fileName;
    @Getter
    private final URL url;
    @Getter
    private AtomicReference<HttpURLConnection> conn;
    @Getter
    private boolean finish = false;
    @Getter
    private final URL fileURL;
    private static boolean downloadDepen = false;
    private static final ThreadPoolExecutor pool;
    private static final File depenDir;

    static {
        pool = new ThreadPoolExecutor(5, 5, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(5));
        depenDir = new File(EasyLib.getInstance().getDataFolder() + File.separator + "Dependencies");
    }

    @SneakyThrows
    public Dependence(String fileName, DependenceData.DependenceUrl url, DependenceData.MD5 MD5) {
        this.fileName = fileName;
        this.MD5 = MD5.getData();
        this.url = new URL(url.getData());
        fileURL = new File(depenDir.getPath() + File.separator + fileName).toURI().toURL();
    }

    public static void download(Dependence dependence) {
        pool.submit(() -> {
            if (!depenDir.exists()) {
                try {
                    Files.createDirectory(Paths.get(depenDir.getPath()));
                } catch (IOException e) {
                    StackTrace.printStackTrace(e);
                }
            }

            File dependenceFile = new File(EasyLib.getInstance().getDataFolder() + File.separator + "Dependencies" + File.separator + dependence.getFileName());

            /*文件不存在*/
            if (!dependenceFile.exists()) {
                sendLackMessage(dependence.getFileName());
                download(dependence.conn, dependenceFile);
                dependence.finish = true;
            } else {
                /*文件存在*/
                if (!dependence.getMD5().equals(BasicUtil.getFileMD5(dependenceFile))) {
                    /*MD5不匹配*/
                    sendInCompleteMessage(dependence.getFileName());
                    download(dependence.conn, dependenceFile);
                }
                dependence.finish = true;
            }

        });
    }

    private static void getResource(Dependence dependence) {
        try {
            dependence.conn = new AtomicReference<>();
            dependence.conn.set((HttpURLConnection) dependence.url.openConnection());
            dependence.conn.get().connect();
        } catch (IOException e) {
            StackTrace.printStackTrace(e);
        }
    }

    private static void download(AtomicReference<HttpURLConnection> conn, File file) {
        try {
            DownloadUtil.download(conn.get(), file, null);
            sendDownloadCompleteMessage(file.getName());
        } catch (IOException e) {
            StackTrace.printStackTrace(e);
        }
    }

    private static void sendLackMessage(String dependency) {
        sendMessage(String.format("§7[§eEasyLib§7]§cLack of dependency: %s, §bstart downloading!", dependency));
    }

    private static void sendDownloadCompleteMessage(String dependency) {
        sendMessage(String.format("§7[§eEasyLib§7]§2Dependency: %s download completed!", dependency));
    }

    private static void sendInCompleteMessage(String dependency) {
        sendMessage(String.format("§7[§eEasyLib§7]§cDependency: %s is incomplete, §bstart downloading!", dependency));
    }

    private static void sendLoadMessage(String dependency) {
        sendMessage(String.format("§7[§eEasyLib§7]§bDependency: %s §2load successfully!", dependency));
    }

    private static void sendMessage(String message) {
        Bukkit.getServer().getConsoleSender().sendMessage(message);
    }

    public static void init() {
        if (downloadDepen) {
            return;
        }
        downloadDepen = true;

        LibData.getScheduledpool().execute(() -> {
            List<Dependence> dependences = new ArrayList<>();
            dependences.add(new Dependence("jackson-databind-2.10.3.jar", DependenceData.Maven.JACKSON_DATABIND, DependenceData.MD5.JACKSON_DATABIND));
            dependences.add(new Dependence("jackson-core-2.10.3.jar", DependenceData.Maven.JACKSON_CORE, DependenceData.MD5.JACKSON_CORE));
            dependences.add(new Dependence("jackson-annotations-2.10.3.jar", DependenceData.Maven.JACKSON_ANNOTATIONS, DependenceData.MD5.JACKSON_ANNOTATIONS));

            if (EasyLib.getInstance().getConfig().getBoolean("Dependencies.Kotlin")) {
                dependences.add(new Dependence("annotations-19.0.0.jar", DependenceData.Maven.KOTLIN_STDLIB_ANNOTATIONS, DependenceData.MD5.KOTLIN_STDLIB_ANNOTATIONS));
                dependences.add(new Dependence("kotlin-stdlib-1.3.72.jar", DependenceData.Maven.KOTLIN_STDLIB, DependenceData.MD5.KOTLIN_STDLIB));
                dependences.add(new Dependence("kotlin-stdlib-common-1.3.72.jar", DependenceData.Maven.KOTLIN_STDLIB_COMMON, DependenceData.MD5.KOTLIN_STDLIB_COMMON));
                dependences.add(new Dependence("kotlin-stdlib-jdk7-1.3.72.jar", DependenceData.Maven.KOTLIN_STDLIB_JDK7, DependenceData.MD5.KOTLIN_STDLIB_JDK7));
                dependences.add(new Dependence("kotlin-stdlib-jdk8-1.3.72.jar", DependenceData.Maven.KOTLIN_STDLIB_JDK8, DependenceData.MD5.KOTLIN_STDLIB_JDK8));
            }

            for (Dependence dependence : dependences) {
                getResource(dependence);
            }

            for (Dependence dependence : dependences) {
                download(dependence);
            }


            while (true) {
                boolean finish = true;
                for (Dependence dependence : dependences) {
                    if (!dependence.isFinish()) {
                        finish = false;
                    }
                }

                if (finish) {
                    break;
                }
            }

            for (Dependence dependence : dependences) {
                try {
                    Agent.addToClassPath(Paths.get(dependence.getFileURL().toURI()));
                } catch (Exception e) {
                    StackTrace.printStackTrace(e);
                }
                sendLoadMessage(dependence.getFileName());
            }

            LibData.setObjectMapper(new ObjectMapper());
            EasyLib.setEasyLibAPI(new EasyLibAPI());
        });


    }

}
