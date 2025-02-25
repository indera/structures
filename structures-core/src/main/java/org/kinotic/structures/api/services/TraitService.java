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

package org.kinotic.structures.api.services;

import org.elasticsearch.search.SearchHits;
import org.kinotic.continuum.api.annotations.Publish;
import org.kinotic.structures.api.domain.AlreadyExistsException;
import org.kinotic.structures.api.domain.PermenentTraitException;
import org.kinotic.structures.api.domain.Trait;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Publish
public interface TraitService {

    Trait save(Trait saveTrait) throws AlreadyExistsException, PermenentTraitException, IOException;

    Optional<Trait> getTraitById(String id) throws IOException;

    Optional<Trait> getTraitByName(String name) throws IOException;

    List<Trait> getAllSystemManaged() throws IOException;

    SearchHits getAll(int numberPerPage, int page, String columnToSortBy, boolean descending) throws IOException;

    SearchHits getAllNameLike(String nameLike, int numberPerPage, int page, String columnToSortBy, boolean descending) throws IOException;

    void delete(String traitId) throws PermenentTraitException, IOException;

    void createTraitIndex();

}
