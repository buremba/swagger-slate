/*
 *
 *  Copyright 2015 Robert Winkler
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */
package org.rakam.client.utils;

import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.DoubleProperty;
import io.swagger.models.properties.FloatProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import io.swagger.models.properties.UUIDProperty;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class PropertyUtils {

    public static String getType(Property property){
        return getType(property, null);
    }

    public static String getType(Property property, Set<String> requiredRefinitions){
        Validate.notNull(property, "property must not be null!");
        String type;
        if(property instanceof RefProperty){
            RefProperty refProperty = (RefProperty)property;
            if(requiredRefinitions != null)
                requiredRefinitions.add(refProperty.getSimpleRef());
            return "["+refProperty.getSimpleRef()+"](#"+refProperty.getSimpleRef().toLowerCase(Locale.ENGLISH)+")";
        }else if(property instanceof ArrayProperty){
            ArrayProperty arrayProperty = (ArrayProperty)property;
            Property items = arrayProperty.getItems();
            type = getType(items, requiredRefinitions) + " " + arrayProperty.getType();
        }else if(property instanceof StringProperty){
            StringProperty stringProperty = (StringProperty)property;
            List<String> enums = stringProperty.getEnum();
            if(enums !=null && !enums.isEmpty()){
                type = "enum" + " (" + StringUtils.join(enums, ", ") + ")";
            }else{
                type = property.getType();
            }
        }
        else{
            if(StringUtils.isNotBlank(property.getFormat())){
                type = StringUtils.defaultString(property.getType()) + " (" + property.getFormat() + ")";
            }else{
                type = property.getType();
            }
        }
        return StringUtils.defaultString(type);
    }

    public static String getDefaultValue(Property property){
        Validate.notNull(property, "property must not be null!");
        String defaultValue = "";
        if(property instanceof BooleanProperty){
            BooleanProperty booleanProperty = (BooleanProperty)property;
            defaultValue = Objects.toString(booleanProperty.getDefault(), "");
        }else if(property instanceof StringProperty){
            StringProperty stringProperty = (StringProperty)property;
            defaultValue = Objects.toString(stringProperty.getDefault(), "");
        }else if(property instanceof DoubleProperty){
            DoubleProperty doubleProperty = (DoubleProperty)property;
            defaultValue = Objects.toString(doubleProperty.getDefault(), "");
        }else if(property instanceof FloatProperty){
            FloatProperty floatProperty = (FloatProperty)property;
            defaultValue = Objects.toString(floatProperty.getDefault(), "");
        }else if(property instanceof IntegerProperty){
            IntegerProperty integerProperty = (IntegerProperty)property;
            defaultValue = Objects.toString(integerProperty.getDefault(), "");
        }
        else if(property instanceof LongProperty){
            LongProperty longProperty = (LongProperty)property;
            defaultValue = Objects.toString(longProperty.getDefault(), "");
        }
        else if(property instanceof UUIDProperty){
            UUIDProperty uuidProperty = (UUIDProperty)property;
            defaultValue = Objects.toString(uuidProperty.getDefault(), "");
        }
        return defaultValue;
    }
}
