package net.sourceforge.ganttproject.action;

import javax.swing.Action;

public class ArtefactDeleteArrowsAction extends ArtefactAction{
    public ArtefactDeleteArrowsAction(ActiveActionProvider provider, Action[] delegates) {
        super("task.removeDependencies", IconSize.TOOLBAR_SMALL, provider, delegates);
    }
}
