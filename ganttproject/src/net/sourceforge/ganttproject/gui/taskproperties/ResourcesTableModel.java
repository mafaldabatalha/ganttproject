/*
GanttProject is an opensource project management tool. License: GPL3
Copyright (C) 2010 Dmitry Barashev

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 3
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package net.sourceforge.ganttproject.gui.taskproperties;

import biz.ganttproject.core.calendar.GanttDaysOff;
import biz.ganttproject.core.time.GanttCalendar;
import net.sourceforge.ganttproject.gui.UIFacade;
import net.sourceforge.ganttproject.language.GanttLanguage;
import net.sourceforge.ganttproject.resource.HumanResource;
import net.sourceforge.ganttproject.roles.Role;
import net.sourceforge.ganttproject.task.ResourceAssignment;
import net.sourceforge.ganttproject.task.ResourceAssignmentCollection;
import net.sourceforge.ganttproject.task.ResourceAssignmentMutator;
import net.sourceforge.ganttproject.gui.UIFacade.Choice;
import net.sourceforge.ganttproject.task.Task;

import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * Table model of a table of resources assigned to a task.
 *
 * @author dbarashev (Dmitry Barashev)
 */
class ResourcesTableModel extends AbstractTableModel {

    static enum Column {
        ID("id", String.class), NAME("resourcename", String.class), UNIT("unit", String.class), COORDINATOR("coordinator",
                Boolean.class), ROLE("role", String.class);

        private final String myName;
        private final Class<?> myClass;

        Column(String key, Class<?> clazz) {
            myName = GanttLanguage.getInstance().getText(key);
            myClass = clazz;
        }

        String getName() {
            return myName;
        }

        Class<?> getColumnClass() {
            return myClass;
        }
    }

    private final List<ResourceAssignment> myAssignments;

    private final ResourceAssignmentMutator myMutator;

    private final UIFacade myUIfacade;

    private boolean isChanged = false;

    public ResourcesTableModel(ResourceAssignmentCollection assignmentCollection, UIFacade uifacade) {
        myAssignments = new ArrayList<ResourceAssignment>(Arrays.asList(assignmentCollection.getAssignments()));
        myMutator = assignmentCollection.createMutator();
        myUIfacade = uifacade;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return Column.values()[columnIndex].getColumnClass();
    }

    @Override
    public int getColumnCount() {
        return Column.values().length;
    }

    @Override
    public int getRowCount() {
        return myAssignments.size() + 1;
    }

    @Override
    public String getColumnName(int col) {
        return Column.values()[col].getName();
    }

    @Override
    public Object getValueAt(int row, int col) {
        Object result;
        if (row >= 0) {
            if (row < myAssignments.size()) {
                ResourceAssignment assignment = myAssignments.get(row);
                switch (col) {
                    case 0:
                        result = String.valueOf(assignment.getResource().getId());
                        break;
                    case 1:
                        result = assignment.getResource();
                        break;
                    case 2:
                        result = String.valueOf(assignment.getLoad());
                        break;
                    case 3:
                        result = new Boolean(assignment.isCoordinator());
                        break;
                    case 4:
                        result = assignment.getRoleForAssignment();
                        break;
                    default:
                        result = "";
                }
            } else {
                result = null;
            }
        } else {
            throw new IllegalArgumentException("I can't return data in row=" + row);
        }
        return result;
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        boolean result = col > 0;
        if (result) {
            result = (col == 2 ? row < myAssignments.size() : row <= myAssignments.size()) || col == 3 || col == 4;
        }
        return result;
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        if (row >= 0) {
            if (row >= myAssignments.size()) {
                createAssignment(value);
            } else {
                updateAssignment(value, row, col);
            }
        } else {
            throw new IllegalArgumentException("I can't set data in row=" + row);
        }
        isChanged = true;
    }

    private void updateAssignment(Object value, int row, int col) {
        ResourceAssignment updateTarget = myAssignments.get(row);
        switch (col) {
            case 4: {
                updateTarget.setRoleForAssignment((Role) value);
                break;
            }
            case 3: {
                updateTarget.setCoordinator(((Boolean) value).booleanValue());
                break;
            }
            case 2: {
                float loadAsFloat = Float.parseFloat(String.valueOf(value));
                updateTarget.setLoad(loadAsFloat);
                break;
            }
            case 1: {
                if (value == null) {
                    updateTarget.delete();
                    myAssignments.remove(row);
                    fireTableRowsDeleted(row, row);
                } else if (value instanceof HumanResource) {
                    float load = updateTarget.getLoad();
                    boolean coord = updateTarget.isCoordinator();
                    updateTarget.delete();
                    myMutator.deleteAssignment(updateTarget.getResource());
                    ResourceAssignment newAssignment = myMutator.addAssignment((HumanResource) value);
                    newAssignment.setLoad(load);
                    newAssignment.setCoordinator(coord);
                    myAssignments.set(row, newAssignment);
                }
                break;

            }
            default:
                break;
        }
    }

    /**
     * Função auxiliar para verificar se a pessoa que vai ser atribuida a uma nova tarefa já tem tarefas para a mesma data.
     *
     * @param humanResource - pessoa a quem esta a vai ser atribuida a tarefa
     * @param task          - tarefa a ser atribuida
     * @return - devolve a escolha feita pelo utilizador quando existe conlflito nas datas. Ou devolve <code> CHOICE.YES </code> caso não haja conflito
     */
    private Choice overlappingTasks(HumanResource humanResource, Task task) {
        GanttCalendar start = task.getStart();
        GanttCalendar end = task.getEnd();
        Choice choice = Choice.YES;
        int numberOfOverlappingDates = humanResource.overlappingDates(start, end);

        if (numberOfOverlappingDates != 0) {
            String msgWithResourceName;
            String resourceName = humanResource.getName();
            if (numberOfOverlappingDates == 1)
                msgWithResourceName = String.format(OVERLOAD_MSG_SINGULAR, resourceName, numberOfOverlappingDates);
            else
                msgWithResourceName = String.format(OVERLOAD_MSG_PLURAL, resourceName, numberOfOverlappingDates);
            choice = getUIFacade().showConfirmationDialog(msgWithResourceName, i18n.getText("warning"));
        }

        return choice;
    }

    /**
     * Função auxiliar para verificar se a pessoa que vai ser atribuida a uma nova tarefa já tem férias marcadas para a data da tarefa.
     *
     * @param humanResource - pessoa a quem esta a vai ser atribuida a tarefa
     * @param task          - tarefa a ser atribuida
     * @return - devolve a escolha feita pelo utilizador quando existe conlflito nas datas. Ou devolve null caso não haja conflito
     */
    private Choice overlappingHolidays(HumanResource humanResource, Task task) {
        GanttCalendar start = task.getStart();
        GanttCalendar end = task.getEnd();
        GanttDaysOff daysOff = humanResource.overlappingHolidays(start, end);
        Choice choice = Choice.YES;

        if (daysOff != null) {
            String holidayStart = daysOff.getStart().toString();
            String holidayEnd = daysOff.getFinish().toString();
            String holidayOverlap = String.format(HOLIDAYS_OVERLAP, humanResource.getName(), holidayStart, holidayEnd);
            choice = getUIFacade().showConfirmationDialog(holidayOverlap, i18n.getText("warning"));
        }

        return choice;
    }

    /**
     * Auxiliary function that verifies if the person is already associated in that task.
     *
     * @param humanResource - assignment to verify
     * @param task          - task to verify
     * @return - <code> true </code> if the person is already associated with the task, otherwise returns <code> false </code>
     */
    private boolean sameTask(HumanResource humanResource, Task task) {
        return humanResource.sameTask(task);
    }

    /**
     * Function that adds more load to a humanResource that has already some load in the task.
     *
     * @param assignment - assignment to verify
     */
    private void addLoadtoResource(ResourceAssignment assignment) {
        Iterator<ResourceAssignment> it = myAssignments.iterator();
        while (it.hasNext()) {
            ResourceAssignment next = it.next();
            if (next.getTask().equals(assignment.getTask())) {
                int newLoad = (int) next.getLoad() + 100;
                if (getUIFacade().showConfirmationDialog(SAME_PERSON_SAME_TASK, i18n.getText("warning")) == Choice.YES)
                    next.setLoad(newLoad);
            }
        }
    }

    private final GanttLanguage i18n = GanttLanguage.getInstance();

    /**
     * Mensagem a exibir quando há apenas uma tarefa com as datas coincidentes.
     */
    private final String OVERLOAD_MSG_SINGULAR = "Sobrecarga de Tarefas! %s ja esta atribuido a %d tarefa neste periodo de tempo. Quer prosseguir com a atribuicao da tarefa?";
    /**
     * Mensagem a exibir quando há mais do que uma tarefa com as datas coincidentes.
     */
    private final String OVERLOAD_MSG_PLURAL = "Sobrecarga de Tarefas! %s ja esta atribuido a %d tarefas neste periodo de tempo. Quer prosseguir com a atribuicao da tarefa?";

    /**
     * Message that shows when that person is already in that task and adds a new load in the task.
     */
    private final String SAME_PERSON_SAME_TASK = "Pessoa ja esta associada a esta tarefa. Deseja aumentar a carga de trabalho?";

    /**
     * Mensagem a exibir quando a pessoa tem férias marcadas para a data da tarefa que lhe está a ser atribuida.
     */
    private final String HOLIDAYS_OVERLAP = "Ferias sobrespostas! %s tem ferias marcadas de %s a %s. Quer atribuir a tarefa a mesma?";

    private void createAssignment(Object value) {
        if (value instanceof HumanResource) {
            HumanResource humanResource = (HumanResource) value;
            ResourceAssignment newAssignment = myMutator.addAssignment(humanResource);
            newAssignment.setLoad(100);
            Task task = newAssignment.getTask();
            Choice choice;
            if (!sameTask(humanResource, task))
                choice = overlappingTasks(humanResource, task);
            else {
                choice = Choice.NO;
                addLoadtoResource(newAssignment);
            }

            if (choice == Choice.YES) {
                choice = overlappingHolidays(humanResource, task);
                if (choice == Choice.YES) {
                    boolean coord = myAssignments.isEmpty();
                    newAssignment.setCoordinator(coord);
                    newAssignment.setRoleForAssignment(newAssignment.getResource().getRole());
                    myAssignments.add(newAssignment);
                    fireTableRowsInserted(myAssignments.size(), myAssignments.size());
                } else myMutator.deleteAssignment(humanResource);
            } else
                myMutator.deleteAssignment(humanResource);
        }
    }

    private UIFacade getUIFacade() {
        return myUIfacade;
    }

    public List<ResourceAssignment> getResourcesAssignments() {
        return Collections.unmodifiableList(myAssignments);
    }

    public void commit() {
        myMutator.commit();
    }

    public boolean isChanged() {
        return isChanged;
    }

    public void delete(int[] selectedRows) {
        List<ResourceAssignment> selected = new ArrayList<ResourceAssignment>();
        for (int row : selectedRows) {
            if (row < myAssignments.size()) {
                selected.add(myAssignments.get(row));
            }
        }
        for (ResourceAssignment ra : selected) {
            ra.delete();
        }
        myAssignments.removeAll(selected);
        fireTableDataChanged();
    }

}
