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

package com.salesforce.dataloader.dyna;


import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.Converter;

/**
 * 
 * Converts Strings to Booleans.
 *
 * @author Lexi Viripaeff
 * @since 6.0
 */

public final class BooleanConverter implements Converter {


    // ----------------------------------------------------------- Constructors

    public BooleanConverter() {

        this.defaultValue = null;
        this.useDefault = false;

    }

    public BooleanConverter(Object defaultValue) {

        this.defaultValue = defaultValue;
        this.useDefault = true;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The default value specified to our Constructor, if any.
     */
    private Object defaultValue = null;


    /**
     * Should we return the default value on conversion errors?
     */
    private boolean useDefault = true;


    // --------------------------------------------------------- Public Methods


    /**
     * Convert the specified input object into an output object of the
     * specified type.
     *
     * @param type Data type to which this value should be converted
     * @param value The input value to be converted
     *
     * @exception ConversionException if conversion cannot be performed
     *  successfully
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object convert(Class type, Object value) {

        if (value == null || value instanceof Boolean) {
            return value;
        }
      
        String stringValue = value.toString().trim();
        if (stringValue.length()==0) {
            return null;
        }

        try {
            if (stringValue.equalsIgnoreCase("yes") ||
                    stringValue.equalsIgnoreCase("y") ||
                    stringValue.equalsIgnoreCase("true") ||
                    stringValue.equalsIgnoreCase("on") ||
                    stringValue.equalsIgnoreCase("1")) {
                return (Boolean.TRUE);
            } else if (stringValue.equalsIgnoreCase("no") ||
                    stringValue.equalsIgnoreCase("n") ||
                    stringValue.equalsIgnoreCase("false") ||
                    stringValue.equalsIgnoreCase("off") ||
                    stringValue.equalsIgnoreCase("0")) {
                return (Boolean.FALSE);
            } else if (useDefault) {
                return (defaultValue);
            } else {
                throw new ConversionException(stringValue);
            }
        } catch (ClassCastException e) {
            if (useDefault) {
                return (defaultValue);
            } else {
                throw new ConversionException(e);
            }
        }

    }


}
