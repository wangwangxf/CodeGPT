package ee.carlrobert.chatgpt.ide.action;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;

public class RefactorAction extends BaseAction {

  protected void initToolWindow(ToolWindow toolWindow) {
    toolWindow.setTitle("Refactor Code");
    toolWindow.show();
  }

  protected void actionPerformed(Project project, Editor editor, String selectedText) {
    sendMessage(project, "Refactor the following code:\n\n" + selectedText);
  }
}
