/*
 *
 * Copyright 2008-2021 Kinotic and the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kinotic.structures.api.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;

@Document(indexName = "structure")
@Setting(shards = 5, replicas = 2)
public class Structure implements Serializable {

    @Id
    @Field(type = FieldType.Keyword)
    private String id = null;
    @Field(type = FieldType.Text)
    private String description = null;
    @Field(type = FieldType.Long)
    private long created = 0;// do not ever set, system managed
    @Field(type = FieldType.Boolean)
    private boolean published = false;
    @Field(type = FieldType.Long)
    private long publishedTimestamp = 0;
    @Field(type = FieldType.Keyword)
    private String name = null;
    @Field(type = FieldType.Keyword)
    private String namespace = null;
    @Field(type = FieldType.Keyword)
    private String itemIndex = null;

    @Field(type = FieldType.Flattened)
    private LinkedHashMap<String, Trait> traits = new LinkedHashMap<>();

    @Field(type = FieldType.Flattened)
    private HashMap<String, String> metadata = new HashMap<>();

    @Version
    @Field(type = FieldType.Long)
    private Long updated;// do not ever set, system managed

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public long getPublishedTimestamp() {
        return publishedTimestamp;
    }

    public void setPublishedTimestamp(long publishedTimestamp) {
        this.publishedTimestamp = publishedTimestamp;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getItemIndex() {
        return itemIndex;
    }

    public void setItemIndex(String itemIndex) {
        this.itemIndex = itemIndex;
    }

    public LinkedHashMap<String, Trait> getTraits() {
        return traits;
    }

    public void setTraits(LinkedHashMap<String, Trait> traits) {
        this.traits = traits;
    }

    public HashMap<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(HashMap<String, String> metadata) {
        this.metadata = metadata;
    }

    public Long getUpdated() {
        return updated;
    }

    public void setUpdated(Long updated) {
        this.updated = updated;
    }

}
