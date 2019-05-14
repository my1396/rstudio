/*
 * UnsavedChangesDialog.java
 *
 * Copyright (C) 2009-19 by RStudio, Inc.
 *
 * Unless you have received this program directly from RStudio pursuant
 * to the terms of a commercial license agreement with RStudio, then
 * this program is licensed to you under the terms of version 3 of the
 * GNU Affero General Public License. This program is distributed WITHOUT
 * ANY EXPRESS OR IMPLIED WARRANTY, INCLUDING THOSE OF NON-INFRINGEMENT,
 * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE. Please refer to the
 * AGPL (http://www.gnu.org/licenses/agpl-3.0.txt) for more details.
 *
 */
package org.rstudio.studio.client.workbench.ui.unsaved;

import java.util.ArrayList;

import org.rstudio.core.client.SafeHtmlUtil;
import org.rstudio.core.client.StringUtil;
import org.rstudio.core.client.widget.ModalDialog;
import org.rstudio.core.client.widget.Operation;
import org.rstudio.core.client.widget.OperationWithInput;
import org.rstudio.core.client.widget.ThemedButton;
import org.rstudio.studio.client.workbench.model.UnsavedChangesTarget;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.ImageResourceCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.IdentityColumn;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.ui.CheckBox;
import com.google.gwt.user.client.ui.HasVerticalAlignment;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.DefaultSelectionEventManager;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.MultiSelectionModel;
import com.google.gwt.view.client.ProvidesKey;

public class UnsavedChangesDialog extends ModalDialog<UnsavedChangesDialog.Result>
{
   public class Result
   {
      public Result(ArrayList<UnsavedChangesTarget> saveTargets,
                    boolean alwaysSave)
      {
         saveTargets_ = saveTargets;
         alwaysSave_ = alwaysSave;
      }
      
      public ArrayList<UnsavedChangesTarget> getSaveTargets()
      {
         return saveTargets_;
      }
      
      public boolean getAlwaysSave()
      {
         return alwaysSave_;
      }
      
      private ArrayList<UnsavedChangesTarget> saveTargets_;
      private boolean alwaysSave_;
   }
   
   public UnsavedChangesDialog(
         String title,
         ArrayList<UnsavedChangesTarget> dirtyTargets,
         final OperationWithInput<Result> saveOperation,
         final Command onCancelled)
   {
      this(title, null, dirtyTargets, saveOperation, onCancelled);
   }
   
   public UnsavedChangesDialog(
         String title,
         String alwaysSaveOption,
         ArrayList<UnsavedChangesTarget> dirtyTargets,
         final OperationWithInput<Result> saveOperation,
         final Command onCancelled)
   {
      super(title,
            DialogRole.AlertDialog,
            saveOperation, 
            onCancelled != null ? new Operation() {
                                    @Override
                                    public void execute()
                                    {
                                       onCancelled.execute();
                                    }} :
                                  null);
      alwaysSaveOption_ = StringUtil.notNull(alwaysSaveOption);
      targets_ = dirtyTargets;
      
      setOkButtonCaption("Save Selected");
      
      addLeftButton(new ThemedButton("Don't Save", new ClickHandler() {
         @Override
         public void onClick(ClickEvent event)
         {
           closeDialog();
           saveOperation.execute(new Result(
                                       new ArrayList<UnsavedChangesTarget>(),
                                       false));
         } 
      }));
   }
   
   @Override
   protected void onDialogShown()
   {
      focusOkButton();
   }

   @Override
   protected Widget createMainWidget()
   {
      // create cell table
      targetsCellTable_ = new CellTable<UnsavedChangesTarget>(
                                          15,
                                          UnsavedChangesCellTableResources.INSTANCE,
                                          KEY_PROVIDER);
      selectionModel_ = new MultiSelectionModel<UnsavedChangesTarget>(KEY_PROVIDER);
      targetsCellTable_.setSelectionModel(
         selectionModel_, 
         DefaultSelectionEventManager.<UnsavedChangesTarget> createCheckboxManager());
      targetsCellTable_.setWidth("100%", true);
      
      // add columns
      addSelectionColumn();
      addIconColumn();
      addNameAndPathColumn();
      
      // hook-up data provider 
      dataProvider_ = new ListDataProvider<UnsavedChangesTarget>();
      dataProvider_.setList(targets_);
      dataProvider_.addDataDisplay(targetsCellTable_);
      targetsCellTable_.setPageSize(targets_.size());
      
      // select all by default
      for (UnsavedChangesTarget editingTarget : dataProvider_.getList())
         selectionModel_.setSelected(editingTarget, true);
      
      // enclose cell table in scroll panel
      ScrollPanel scrollPanel = new ScrollPanel();
      scrollPanel.setStylePrimaryName(RESOURCES.styles().targetScrollPanel());
      scrollPanel.setWidget(targetsCellTable_);
      if (dataProvider_.getList().size() > 4)
         scrollPanel.setHeight("280px");
      
      // always save check box (may not be shown)
      chkAlwaysSave_ = new CheckBox(alwaysSaveOption_);
      
      // main widget
      VerticalPanel panel = new VerticalPanel();
      Label captionLabel = new Label(
                           "The following files have unsaved changes:");
      captionLabel.setStylePrimaryName(RESOURCES.styles().captionLabel());
      panel.add(captionLabel);
      panel.add(scrollPanel);      
      if (!StringUtil.isNullOrEmpty(alwaysSaveOption_))
      { 
         panel.add(chkAlwaysSave_);
         panel.setCellHeight(chkAlwaysSave_, "30px");
         panel.setCellVerticalAlignment(chkAlwaysSave_,
                                        HasVerticalAlignment.ALIGN_MIDDLE);
      }
      
      return panel;
   }
   
   private Column<UnsavedChangesTarget, Boolean> addSelectionColumn()
   {
      Column<UnsavedChangesTarget, Boolean> checkColumn = 
         new Column<UnsavedChangesTarget, Boolean>(new CheckboxCell(true, false)) 
         {
            @Override
            public Boolean getValue(UnsavedChangesTarget object)
            {
               return selectionModel_.isSelected(object);
            }   
         };
      checkColumn.setVerticalAlignment(HasVerticalAlignment.ALIGN_TOP);
      targetsCellTable_.addColumn(checkColumn); 
      targetsCellTable_.setColumnWidth(checkColumn, 25, Unit.PX);
      
      return checkColumn;
   }
  
   
   private Column<UnsavedChangesTarget, ImageResource> addIconColumn()
   {
      Column<UnsavedChangesTarget, ImageResource> iconColumn = 
         new Column<UnsavedChangesTarget, ImageResource>(new ImageResourceCell()) {

            @Override
            public ImageResource getValue(UnsavedChangesTarget object)
            {
               return object.getIcon();
            }
         };
      targetsCellTable_.addColumn(iconColumn);
      targetsCellTable_.setColumnWidth(iconColumn, 20, Unit.PX);
    
      return iconColumn;
   }
    
   private class NameAndPathCell extends AbstractCell<UnsavedChangesTarget>
   {

      @Override
      public void render(
            com.google.gwt.cell.client.Cell.Context context,
            UnsavedChangesTarget value, SafeHtmlBuilder sb)
      {
         if (value != null) 
         {
           Styles styles = RESOURCES.styles();
           
           String path = value.getPath();
           if (path != null)
           {
              SafeHtmlUtil.appendDiv(sb, styles.targetName(), value.getTitle());
              SafeHtmlUtil.appendDiv(sb, styles.targetPath(), path); 
           }
           else
           {
              SafeHtmlUtil.appendDiv(sb, 
                                     styles.targetUntitled(), 
                                     value.getTitle());
           }
         }
         
      }
      
   }
   
   private IdentityColumn<UnsavedChangesTarget> addNameAndPathColumn()
   {
      IdentityColumn<UnsavedChangesTarget> nameAndPathColumn = 
         new IdentityColumn<UnsavedChangesTarget>(new NameAndPathCell());
      
      targetsCellTable_.addColumn(nameAndPathColumn);
      targetsCellTable_.setColumnWidth(nameAndPathColumn, 350, Unit.PX);
      return nameAndPathColumn;
   }
   
   @Override
   protected Result collectInput()
   {
      return new Result(new ArrayList<UnsavedChangesTarget>(
                                          selectionModel_.getSelectedSet()),
                        chkAlwaysSave_.getValue());
   }

   @Override
   protected boolean validate(Result input)
   {
      return true;
   }
   
   static interface Styles extends CssResource
   {
      String targetScrollPanel();
      String captionLabel();
      String targetName();
      String targetPath();
      String targetUntitled();
   }

   static interface Resources extends ClientBundle
   {
      @Source("UnsavedChangesDialog.css")
      Styles styles();
   }

   static Resources RESOURCES = (Resources) GWT.create(Resources.class);

   public static void ensureStylesInjected()
   {
      RESOURCES.styles().ensureInjected();
   }
   
   private static final ProvidesKey<UnsavedChangesTarget> KEY_PROVIDER = 
      new ProvidesKey<UnsavedChangesTarget>() {
         @Override
         public Object getKey(UnsavedChangesTarget item)
         {
            return item.getId();
         }
    };
   
   private final ArrayList<UnsavedChangesTarget> targets_;
   
   private CellTable<UnsavedChangesTarget> targetsCellTable_; 
   private ListDataProvider<UnsavedChangesTarget> dataProvider_;
   private MultiSelectionModel<UnsavedChangesTarget> selectionModel_;
   
   private final String alwaysSaveOption_;
   private CheckBox chkAlwaysSave_;


}
