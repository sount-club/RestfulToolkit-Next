package com.sount.restful.codegen;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import com.sount.utils.ToolkitUtil;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;


public class SpringBootGenerator extends AnAction {
    Project project;

    @Override
    public void actionPerformed(AnActionEvent e) {
        project = e.getProject();
        if (project == null) {
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }

        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile == null) {
            return;
        }
        String currentPathName = virtualFile.getName();
        String content = editor.getDocument().getText();
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);




//        createPackage();
//        createFile()

        String currentPath = getCurrentPath(e);
/*        String basePath = currentPath.replace("contract/" + className + ".java", "");
        String basePackage = getPackageName(basePath);
        String modelName = className.substring(0, className.indexOf("Contract"));*/


    }


    public void createFile(String fileName,String content) {
        PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, FileTypes.PLAIN_TEXT, content);

        PsiDirectory directory = PsiManager.getInstance(project).findDirectory(com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(project.getBasePath()));
        PsiFile file = directory.findFile(fileName);
        if (file == null) {
            ToolkitUtil.runWriteAction(() -> directory.add(psiFile));
        } else {
            ToolkitUtil.runWriteAction(() -> {
                file.delete();
                directory.add(psiFile);
            });
        }
    }

    private String genFromTemplate(String templateName, Map<String ,String > dataMap) {

        VelocityContext context = new VelocityContext();

//        context.put("services", services);

        String path = "template/";
        String templatePath = path + templateName;
        InputStream input = getClass().getClassLoader().getResourceAsStream(templatePath);

        VelocityEngine engine = new VelocityEngine();
        Properties props = new Properties();
        props.put("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.SimpleLog4JLogSystem");
        props.put("runtime.log.logsystem.log4j.category", "velocity");
        props.put("runtime.log.logsystem.log4j.logger", "velocity");

        engine.init(props);
        StringWriter writer = new StringWriter();
        engine.evaluate(context, writer, "REST", new InputStreamReader(input));

        return writer.toString().replace("\n", "").replace("\r", "");
    }


    // 首先判断要生成的目录和文件和目录是否存在，如果任何一个存在，则退出

    // 创建目录，文件
    private void createController(String basePackage, String path, String modelName) {

        createFile(modelName +"Controller" , genFromTemplate("controller",new HashMap<>())) ;
    }

    private void refreshProject(AnActionEvent e) {
        VirtualFile baseDir = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(e.getProject().getBasePath());
        if (baseDir != null) {
            baseDir.refresh(false, true);
        }
    }

    private String getCurrentPath(AnActionEvent e) {
        VirtualFile currentFile = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
        if (currentFile != null) {
            return currentFile.getPath();
        }
        return null;
    }
}
