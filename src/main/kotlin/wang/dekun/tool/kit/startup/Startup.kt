package wang.dekun.tool.kit.startup

import  wang.dekun.tool.kit.services.ServiceUtils
import  wang.dekun.tool.kit.services.UnregisterActionService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import java.nio.file.Files


/**
 *
 * @author WangDeKun
 * <p>
 * Email :  wangdekunemail@gmail.com
 *
 */


class Startup : ProjectActivity {

    override suspend fun execute(project: Project) {
        ServiceUtils.setOneClickOpenFile(project)
        UnregisterActionService.unregister()
        //破解测试代码
        // Stream<Path> list = Files.list(com.intellij.openapi.application.PathManager.getConfigDir().toAbsolutePath());
        // list.filter(path -> {
        //     return path.toFile().getAbsolutePath().endsWith("64.vmoptions");
        // }).forEach(path2 -> {
        //     System.out.println(path2.toFile().getAbsolutePath());
        // });
        // 使用 Files.list 的完整写法
//        val list = Files.list(com.intellij.openapi.application.PathManager.getConfigDir().toAbsolutePath())
//        list.filter { path ->
//            path.toFile().absolutePath.endsWith("64.vmoptions")
//        }.forEach { path2 ->
//            println(path2.toFile().absolutePath)
//        }

    }
}

//        val actionManager = ActionManager.getInstance()
//        val actionIdList = actionManager.getActionIdList("")
//        actionIdList.forEach { id ->
//            actionManager.getAction(id)?.let { action ->
//                if (action is DefaultActionGroup) {
//                    action.getChildren(null).forEach {
//                        if (it.javaClass.name.contains("ai.codegeex.plugin.actions")) {
////                                        println("actionId = ${it.javaClass.name}")
//                            //ai.codegeex.plugin.actions.ProjectMapTitleAction
//                            if (it.javaClass.name.contains("ai.codegeex.plugin.actions.ProjectMapTitleAction")) {
//                                action.remove(it)
//                            }
//                        }
//                    }
//                }
//            }
//        }