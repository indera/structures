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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kinotic.continuum.api.Identifiable;
import org.kinotic.continuum.idl.api.ObjectTypeDefinition;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Getter
@Setter
@Accessors(chain = true)
@NoArgsConstructor
@Document(indexName = "structure")
@Setting(shards = 5, replicas = 2)
public class Structure implements Identifiable<String> {

    @Id
    @Field(type = FieldType.Keyword)
    private String id = null;

    @Field(type = FieldType.Keyword)
    private String name = null;

    @Field(type = FieldType.Keyword)
    private String namespace = null;

    @Field(type = FieldType.Text)
    private String description = null;

    //@Field(type=FieldType.Date, format = DateFormat.epoch_millis)
    @Field(type = FieldType.Long)
    private long created = 0;// do not ever set, system managed

    //@Field(type=FieldType.Date, format = DateFormat.epoch_millis)
    @Field(type = FieldType.Long)
    private long updated = 0;// do not ever set, system managed

    @Field(type = FieldType.Boolean)
    private boolean published = false;

    //@Field(type=FieldType.Date, format = DateFormat.epoch_millis)
    @Field(type = FieldType.Long)
    private long publishedTimestamp = 0;

    @Field(type = FieldType.Keyword)
    private String itemIndex = null;

    @Field(type = FieldType.Flattened)
    private ObjectTypeDefinition itemDefinition = null;

}
