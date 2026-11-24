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
    }
}