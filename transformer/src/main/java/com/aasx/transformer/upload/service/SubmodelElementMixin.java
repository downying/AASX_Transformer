package com.aasx.transformer.upload.service;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElement;
import org.eclipse.digitaltwin.aas4j.v3.model.File;
import org.eclipse.digitaltwin.aas4j.v3.model.Property;
import org.eclipse.digitaltwin.aas4j.v3.model.SubmodelElementCollection;

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "modelType",
    visible = true
)
@JsonSubTypes({
    @Type(value = File.class, name = "File"),
    @Type(value = Property.class, name = "Property"),
    @Type(value = SubmodelElementCollection.class, name = "SubmodelElementCollection")
})
public interface SubmodelElementMixin {}
