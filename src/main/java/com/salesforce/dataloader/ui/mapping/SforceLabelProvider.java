/*
 * Copyright (c) 2015, salesforce.com, inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *
 *    Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 *    Redistributions in binary form must reproduce the above copyright notice, this list of conditions and
 *    the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 *    Neither the name of salesforce.com, inc. nor the names of its contributors may be used to endorse or
 *    promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dataloader.ui.mapping;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;

import com.salesforce.dataloader.client.DescribeRefObject;
import com.salesforce.dataloader.controller.Controller;
import com.salesforce.dataloader.ui.MappingDialog;
import com.sforce.soap.partner.Field;


/**
 * This class provides the labels for PlayerTable
 */
public class SforceLabelProvider implements ITableLabelProvider {


    // Constructs a PlayerLabelProvider
    public SforceLabelProvider(Controller controller) {
    }


    @Override
    public Image getColumnImage(Object arg0, int arg1) {
        return null;
    }



    /**
     * Gets the text for the specified column
     * 
     * @param arg0 the player
     * @param arg1 the column
     * @return String
     */
    @Override
    public String getColumnText(Object arg0, int arg1) {
        Field field = (Field) arg0;
        boolean isReferenceField = false;
        String[] referenceTos = field.getReferenceTo();
        if (referenceTos != null && referenceTos.length > 0) {
            isReferenceField = true;
        }
        String text = "";
        switch (arg1) {
        case MappingDialog.FIELD_NAME:
            text = field.getName();
            if (isReferenceField && !text.contains(":")) {
                text = text + ":Id";
            }
            break;
        case MappingDialog.FIELD_LABEL:
            text = field.getLabel();
            break;
        case MappingDialog.FIELD_TYPE:
            text = field.getType().toString();
            if ("string".equalsIgnoreCase(text) || "textarea".equalsIgnoreCase(text)) {
                text = text
                        + "("
                        + field.getLength()
                        +")";
            }
            if ("reference".equalsIgnoreCase(text)) {
                text = "Lookup";
                if (isReferenceField) {
                    if (referenceTos.length >= DescribeRefObject.MAX_PARENT_OBJECTS_IN_REFERENCING_FIELD) {
                        text = text + " (" + referenceTos.length + " objects)";
                    } else {
                        for (int i = 0; i < referenceTos.length; i++) {
                            String refEntityName = referenceTos[i];
                            if (i == 0) {
                                text = text + " (" + refEntityName;
                            } else {
                                text = text + ", " + refEntityName;
                            }
                        }
                        text = text +")";
                    }
                }
            }
            break;
        }
        return text;
    }

    /**
     * Adds a listener
     * 
     * @param arg0 the listener
     */
    @Override
    public void addListener(ILabelProviderListener arg0) {
        // Throw it away
    }

    /**
     * Dispose any created resources
     */
    @Override
    public void dispose() {
        // Dispose the image
    }

    /**
     * Returns whether the specified property, if changed, would affect the label
     * 
     * @param arg0 the player
     * @param arg1 the property
     * @return boolean
     */
    @Override
    public boolean isLabelProperty(Object arg0, String arg1) {
        return false;
    }

    /**
     * Removes the specified listener
     * 
     * @param arg0 the listener
     */
    @Override
    public void removeListener(ILabelProviderListener arg0) {
        // Do nothing
    }
}