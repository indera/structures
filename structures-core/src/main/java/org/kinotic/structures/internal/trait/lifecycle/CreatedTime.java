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

package org.kinotic.structures.internal.trait.lifecycle;

import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.TypeCheckMap;
import org.kinotic.structures.api.domain.traitlifecycle.HasOnBeforeModify;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CreatedTime implements HasOnBeforeModify {
    @Override
    public TypeCheckMap beforeModify(TypeCheckMap obj, Structure structure, String fieldName, Map<String, Object> context) throws Exception {
        if(!obj.has("createdTime")){
            obj.amend("createdTime", System.currentTimeMillis());
        }
        return obj;
    }
}
