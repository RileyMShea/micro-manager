///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio/utils
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Apr 11, 2011
//
// COPYRIGHT:    University of California, San Francisco, 2011
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


package org.micromanager.internal.utils;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.LayoutStyle;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import org.micromanager.internal.utils.FileDialogs.FileType;

/**
 * Dialog to link HotKeys to actions (such as Beanshell scripts).
 *
 * @author Nico Stuurman
 */
public final class HotKeysDialog extends JDialog {
   private final ShortCutTableModel sctModel_ = new ShortCutTableModel();
   private final JComboBox<String> combo_ = new JComboBox<>();
   private Integer lastTypedKey_ = 0;
   private final KeyEvtHandler keh_;
   private final ArrayList<Integer> keys_ = new ArrayList<>();
   private final ArrayList<HotKeyAction> actions_ = new ArrayList<>();
   private final ArrayList<HotKeyAction> possibleActions_ = new ArrayList<>();
   private String[] possibleActionsAsString_;
   private final Font ourFont_ = new Font("Lucida Grande", java.awt.Font.PLAIN, 10);
   private JButton addButton_;
   private JTable hotKeyTable_;
   private JScrollPane jScrollPane1_;
   private JButton removeButton_;

   public static FileType MM_HOTKEYS
         = new FileType("MM_HOTKEYS",
         "Micro-Manager HotKeys",
         System.getProperty("user.home") + "/MMHotKeys",
         false, (String[]) null);

   public final class ShortCutTableModel extends AbstractTableModel {

      private static final int COLUMNCOUNT = 2;

      @Override
      public int getRowCount() {
         if (keys_ != null) {
            return keys_.size();
         }
         return 0;
      }

      @Override
      public int getColumnCount() {
         return COLUMNCOUNT;
      }

      @Override
      public String getColumnName(int columnIndex) {
         if (columnIndex == 0) {
            return "Action";
         }
         return "HotKey";
      }

      @Override
      public Object getValueAt(int row, int column) {
         if (column == 0) {
            if (row > actions_.size()) {
               return null;
            }
            HotKeyAction action = actions_.get(row);
            if (action != null) {
               if (action.type_ == HotKeyAction.GUICOMMAND) {
                  return HotKeyAction.GUIITEMS[action.guiCommand_];
               } else {
                  return action.beanShellScript_.getName();
               }
            }
         }
         if (column == 1) {
            if (row > keys_.size()) {
               return null;
            }
            return KeyEvent.getKeyText(keys_.get(row));
         }
         return null;
      }

      @Override
      public boolean isCellEditable(int row, int col) {
         return true;
      }

      @Override
      public void setValueAt(Object value, int row, int col) {
         boolean found = false;
         if (col == 0) {
            for (int i = 0; i < possibleActionsAsString_.length && !found; i++) {
               if (possibleActionsAsString_[i].equals(value)) {
                  found = true;
                  actions_.set(row, possibleActions_.get(i));
               }
            }
         }
         if (col == 1) {
            // keep the keys unique
            if (!keys_.contains((Integer) value)) {
               keys_.set(row, (Integer) value);
            }
         }

         fireTableCellUpdated(row, col);

      }
   }


   /**
    * Creates a new HotKeys Dialog.
    */
   public HotKeysDialog() {
      initComponents();

      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      super.setBounds(100, 100, 377, 378);
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);

      readKeys();

      HotKeys.active_ = false;

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent arg0) {
            hotKeyTable_.getColumnModel().getColumn(0).getCellEditor().stopCellEditing();
            hotKeyTable_.getColumnModel().getColumn(1).getCellEditor().stopCellEditing();
            generateKeys();

            HotKeys.active_ = true;
         }

      });

      updateComboBox();

      hotKeyTable_.setRowSelectionAllowed(false);
      hotKeyTable_.setSelectionBackground(Color.white);

      hotKeyTable_.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(combo_));
      hotKeyTable_.getColumnModel().getColumn(1).setCellEditor(new HotKeyCol1Editor());

      hotKeyTable_.getColumnModel().getColumn(0).setCellRenderer(new ActionCollRenderer());
      hotKeyTable_.getColumnModel().getColumn(1).setCellRenderer(new HotKeyCol1Renderer());

      keh_ = new KeyEvtHandler();
      hotKeyTable_.addKeyListener(keh_);

      super.setModal(true);
      super.setVisible(true);
   }

   /**
    * copy KEYS and actions_ back to HotKeys.KEYS.
    */
   private void generateKeys() {
      HotKeys.KEYS.clear();
      for (int i = 0; i < keys_.size(); i++) {
         HotKeys.KEYS.put(keys_.get(i), actions_.get(i));
      }
   }

   /**
    * Copy the map with hotkeys and action temporarily into two ArrayLists.
    * Those will be used by our table model and written back to HotKeys.KEYS
    * on exit
    */
   private void readKeys() {
      keys_.clear();
      actions_.clear();
      for (Map.Entry<Integer, HotKeyAction> pairs : HotKeys.KEYS.entrySet()) {
         keys_.add(pairs.getKey());
         actions_.add(pairs.getValue());
      }
   }

   public void updateComboBox() {
      // Add Beanshell scripts
      int nrScripts = 0;
      ArrayList<File> scriptList = org.micromanager.internal.script.ScriptPanel.getScriptList();
      if (scriptList != null) {
         nrScripts = scriptList.size();
      }

      possibleActionsAsString_ = new String[HotKeyAction.NRGUICOMMANDS + nrScripts];
      System.arraycopy(HotKeyAction.GUIITEMS, 0, possibleActionsAsString_,
            0, HotKeyAction.GUIITEMS.length);
      for (int i = 0; i < HotKeyAction.NRGUICOMMANDS; i++) {
         possibleActions_.add(i, new HotKeyAction(i));
      }

      if (scriptList != null) {
         for (int i = 0; i < scriptList.size(); i++) {
            possibleActions_.add(i + HotKeyAction.NRGUICOMMANDS,
                  new HotKeyAction(scriptList.get(i)));
            possibleActionsAsString_[i + HotKeyAction.NRGUICOMMANDS] = scriptList.get(i).getName();
         }
      }

      DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(possibleActionsAsString_);
      combo_.setModel(model);
      combo_.setFont(ourFont_);
   }

   public final class ActionCollRenderer extends DefaultTableCellRenderer {
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
                                                     boolean isSelected, boolean hasFocus,
                                                     int row, int column) {
         Component res = super.getTableCellRendererComponent(table, value, isSelected,
               hasFocus, row, column);
         res.setBackground(Color.white);
         res.setForeground(Color.black);
         return res;
      }
   }

   public final class HotKeyCol1Renderer extends DefaultTableCellRenderer {
      public void setHotKeyValue(Object value) {
         setText(KeyEvent.getKeyText((Integer) value));
      }

      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
                                                     boolean isSelected, boolean hasFocus,
                                                     int row, int column) {
         Component res = super.getTableCellRendererComponent(table, value, isSelected,
               hasFocus, row, column);
         res.setBackground(Color.white);
         res.setForeground(Color.black);
         return res;
      }
   }

   public final class KeyEvtHandler implements KeyListener {
      JLabel label_;

      public void setLabel(JLabel label) {
         label_ = label;
      }

      @Override
      public void keyTyped(KeyEvent ke) {
      }

      @Override
      public void keyPressed(KeyEvent ke) {
         Integer value = ke.getKeyCode();

         if (!keys_.contains(value)
               || value.intValue() == keys_.get(hotKeyTable_.getSelectedRow())) {
            lastTypedKey_ = value;
            if (label_ != null) {
               label_.setText(KeyEvent.getKeyText(lastTypedKey_));
            }
         }
      }

      @Override
      public void keyReleased(KeyEvent ke) {
      }
   }

   public final class HotKeyCol1Editor extends AbstractCellEditor implements TableCellEditor {
      JLabel keyLabel = new JLabel();

      // This method is called when a cell value is edited by the user.
      @Override
      public Component getTableCellEditorComponent(javax.swing.JTable table, Object value,
                                                   boolean isSelected, int rowIndex, int colIndex) {
         // 'value' is value contained in the cell located at (rowIndex, colIndex)

         if (value != null) {
            keyLabel.setText((String) value);
         } else {
            keyLabel.setText("m");
         }
         keyLabel.setForeground(Color.red);
         keyLabel.setFocusable(true);
         keyLabel.setFont(ourFont_);
         lastTypedKey_ = keys_.get(rowIndex);
         keh_.setLabel(keyLabel);

         return keyLabel;
      }

      // This method is called when editing is completed.
      // It must return the new value to be stored in the cell.
      @Override
      public Object getCellEditorValue() {
         return lastTypedKey_;
      }
   }


   /**
    * This method is called from within the constructor to initialize the form.
    */
   private void initComponents() {
      jScrollPane1_ = new javax.swing.JScrollPane();
      hotKeyTable_ = new javax.swing.JTable();
      addButton_ = new javax.swing.JButton();
      removeButton_ = new javax.swing.JButton();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setModalExclusionType(java.awt.Dialog.ModalExclusionType.APPLICATION_EXCLUDE);

      jScrollPane1_.setMinimumSize(new java.awt.Dimension(23, 15));
      jScrollPane1_.setPreferredSize(new java.awt.Dimension(32767, 32767));

      hotKeyTable_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      hotKeyTable_.setModel(sctModel_);
      jScrollPane1_.setViewportView(hotKeyTable_);

      addButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      addButton_.setText("Add");
      addButton_.setMinimumSize(new java.awt.Dimension(75, 20));
      addButton_.setPreferredSize(new java.awt.Dimension(75, 20));
      addButton_.addActionListener(this::addButton_ActionPerformed);

      removeButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      removeButton_.setText("Remove");
      removeButton_.setMinimumSize(new Dimension(75, 20));
      removeButton_.setPreferredSize(new Dimension(75, 20));
      removeButton_.addActionListener(this::removeButton_ActionPerformed);

      javax.swing.GroupLayout layout = new GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addComponent(addButton_, GroupLayout.PREFERRED_SIZE,
                              GroupLayout.DEFAULT_SIZE,
                              GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(removeButton_, GroupLayout.PREFERRED_SIZE,
                              GroupLayout.DEFAULT_SIZE,
                              GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 225, Short.MAX_VALUE))
                  .addComponent(jScrollPane1_, GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)
      );
      layout.setVerticalGroup(
            layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                  .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                              .addComponent(addButton_, GroupLayout.PREFERRED_SIZE, 20,
                                    GroupLayout.PREFERRED_SIZE)
                              .addComponent(removeButton_, GroupLayout.PREFERRED_SIZE, 20,
                                    GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1_, GroupLayout.DEFAULT_SIZE,
                              GroupLayout.DEFAULT_SIZE,
                              Short.MAX_VALUE)
                        .addGap(0, 0, Short.MAX_VALUE))
      );

      pack();
   }

   private void addButton_ActionPerformed(ActionEvent evt) {
      keys_.add(32);
      actions_.add(new HotKeyAction(0));
      sctModel_.fireTableRowsInserted(keys_.size() - 1, keys_.size());
      sctModel_.fireTableDataChanged();
   }

   private void removeButton_ActionPerformed(ActionEvent evt) {
      hotKeyTable_.getColumnModel().getColumn(0).getCellEditor().stopCellEditing();
      hotKeyTable_.getColumnModel().getColumn(1).getCellEditor().stopCellEditing();
      int[] rows = hotKeyTable_.getSelectedRows();
      for (int i = rows.length - 1; i >= 0; i--) {
         keys_.remove(rows[i]);
         actions_.remove(rows[i]);
      }
      sctModel_.fireTableRowsDeleted(rows[0], rows[rows.length - 1]);
      sctModel_.fireTableDataChanged();
   }

}
