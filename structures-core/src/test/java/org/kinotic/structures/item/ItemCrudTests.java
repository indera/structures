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

package org.kinotic.structures.item;

import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kinotic.structures.ElasticsearchTestBase;
import org.kinotic.structures.api.domain.AlreadyExistsException;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.Trait;
import org.kinotic.structures.api.domain.TypeCheckMap;
import org.kinotic.structures.api.services.TraitService;
import org.kinotic.structures.internal.api.services.ItemServiceInternal;
import org.kinotic.structures.internal.api.services.StructureServiceInternal;
import org.kinotic.structures.util.StructureTestHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class ItemCrudTests extends ElasticsearchTestBase {

    @Autowired
    private ItemServiceInternal itemService;
    @Autowired
    private TraitService traitService;
    @Autowired
    private StructureServiceInternal structureService;
    @Autowired
    private StructureTestHelper structureTestHelper;

    @Test
    public void createAndDeleteItem() throws Exception {

        Structure structure = structureTestHelper.getSimpleItemStructure();

        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.put("ip", "192.0.2.11");
        obj.put("mac", "000000000001");

        TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);

        Thread.sleep(1000);// give time for ES to flush the new item

        itemService.delete(structure.getId(), saved.getString("id"), null);

        Thread.sleep(1000);

        structureService.delete(structure.getId());

    }

    @Test
    public void createAndDeleteItem_checkingCountBeforeDelete() throws Exception {

        Structure structure = structureTestHelper.getSimpleItemStructure();

        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.put("ip", "192.0.2.11");
        obj.put("mac", "000000000001");

        TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);

        Thread.sleep(1000);// give time for ES to flush the new item

        long count = itemService.count(structure.getId(), null);
        Assertions.assertEquals(1, count);

        SearchHits hits = itemService.getAll(structure.getId(), 100, 0, null);
        Assertions.assertEquals(1, hits.getHits().length);
        Assertions.assertEquals(1, hits.getTotalHits().value);

        Thread.sleep(1000);

        itemService.delete(structure.getId(), saved.getString("id"), null);

        Thread.sleep(1000);

        structureService.delete(structure.getId());

    }

    @Test
    public void createAndDeleteItem_canWeGetById() throws Exception {

        Structure structure = structureTestHelper.getSimpleItemStructure();

        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.put("ip", "192.0.2.11");
        obj.put("mac", "000000000001");

        TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);

        Thread.sleep(1000);

        itemService.delete(structure.getId(), saved.getString("id"), null);

        Thread.sleep(1000);

        Optional<TypeCheckMap> deleted = itemService.getById(structure, saved.getString("id"), null);

        // we should still be able to get this item if it was deleted, they still exist just filtered out by default
        // TODO: we might want to make the deletion route configurable - fully delete item or mark it as deleted and filter
        Assertions.assertTrue(deleted.isPresent());

        structureService.delete(structure.getId());

    }

    @Test
    public void createAndDeleteItem_checkingCountAfterDelete() throws Exception {

        Structure structure = structureTestHelper.getSimpleItemStructure();

        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.put("ip", "192.0.2.11");
        obj.put("mac", "000000000001");

        TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);

        Thread.sleep(1000);

        itemService.delete(structure.getId(), saved.getString("id"), null);

        Thread.sleep(1000);

        long count = itemService.count(structure.getId(), null);
        Assertions.assertEquals(0, count);

        SearchHits hits = itemService.getAll(structure.getId(), 100, 0, null);
        Assertions.assertEquals(0, hits.getHits().length);
        Assertions.assertEquals(0, hits.getTotalHits().value);

        Thread.sleep(1000);

        structureService.delete(structure.getId());

    }


    @Test
    public void createAndupsertItem() throws Exception {

        Structure structure = structureTestHelper.getSimpleItemStructure();

        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.put("ip", "192.0.2.11");
        obj.put("mac", "000000000001");

        TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);

        try {
            Thread.sleep(1000);// give time for ES to flush the new item

            saved = itemService.getItemById(structure.getId(), saved.getString("id"), null).get();

            if (!saved.getString("mac").equals("000000000001")) {
                throw new IllegalStateException("Data provided to Item apon saving and getting");
            }


            saved.put("mac", "aaaaddddrrrr");

            itemService.upsertItem(structure.getId(), saved, null);

            saved = itemService.getItemById(structure.getId(), saved.getString("id"), null).get();

            if (!saved.getString("mac").equals("aaaaddddrrrr")) {
                throw new IllegalStateException("Data provided to Item apon saving and getting");
            }


        } catch (AlreadyExistsException e) {
            throw e;
        } finally {
            itemService.delete(structure.getId(), saved.getString("id"), null);

            Thread.sleep(1000);

            structureService.delete(structure.getId());
        }
    }

    @Test
    public void validatePrimaryKeyWithTwoFields() throws Exception {

        Structure structure = new Structure();
        structure.setName("Item3-" + System.currentTimeMillis());
        structure.setNamespace("my.funky-Namespace_");
        structure.setDescription("Defines an Person");

        Optional<Trait> stateOptional = traitService.getTraitByName("KeywordString");
        Optional<Trait> cityOptional = traitService.getTraitByName("KeywordString");
        Optional<Trait> addressOptional = traitService.getTraitByName("KeywordString");
        Optional<Trait> firstNameOptional = traitService.getTraitByName("KeywordString");
        Optional<Trait> lastNameOptional = traitService.getTraitByName("KeywordString");

        structure.getTraits().put("state", stateOptional.get());
        structure.getTraits().put("city", cityOptional.get());
        structure.getTraits().put("address", addressOptional.get());
        structure.getTraits().put("firstName", firstNameOptional.get());
        structure.getTraits().put("lastName", lastNameOptional.get());

        // should also get createdTime, updateTime, and deleted by default

        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.put("state", "Nevada");
        obj.put("city", "Las Vegas");
        obj.put("address", "111 Las Vegas Blvd");
        obj.put("firstName", "Marco");
        obj.put("lastName", "Polo");
        obj.put("id", "nevada-las_vegas-111_las_vegas_blvd");

        structure = structureService.save(structure);
        structureService.publish(structure.getId());
        TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);

        try {
            Thread.sleep(1000);// give time for ES to flush the new item

            Optional<TypeCheckMap> freshOpt = itemService.getItemById(structure.getId(), "nevada-las_vegas-111_las_vegas_blvd", null);

            if(freshOpt.isEmpty()){
                throw new IllegalStateException("Composite Primary Key was not saved as expected");
            }

            TypeCheckMap fresh = freshOpt.get();

            if (!fresh.getString("firstName").equals("Marco")) {
                throw new IllegalStateException("Data provided to upsert was not saved properly");
            }

            fresh.put("firstName", "The");
            fresh.put("lastName", "Dude");

            TypeCheckMap updated = itemService.upsertItem(structure.getId(), fresh, null);

            if (!updated.getString("firstName").equals("The") || !updated.getString("lastName").equals("Dude")) {
                throw new IllegalStateException("Data provided to upsert was not saved properly");
            }

            TypeCheckMap secondGet = itemService.getItemById(structure.getId(), "nevada-las_vegas-111_las_vegas_blvd", null).get();

            if (!secondGet.getString("firstName").equals("The") || !secondGet.getString("lastName").equals("Dude")) {
                throw new IllegalStateException("Data provided to upsert was not saved properly");
            }

        } catch (AlreadyExistsException e) {
            throw e;
        } finally {
            itemService.delete(structure.getId(), saved.getString("id"), null);

            Thread.sleep(1000);

            structureService.delete(structure.getId());
        }
    }

    @Test
    public void upsertItemThenAddFieldAndupsertItem() throws Exception {

        Structure structure = structureTestHelper.getSimpleItemStructure();

        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.put("ip", "192.0.2.101");
        obj.put("vpnIp", "10.0.2.101");
        obj.put("mac", "000000000001");

        TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);

        try {
            Thread.sleep(1000);// give time for ES to flush the new item

            saved = itemService.getItemById(structure.getId(), saved.getString("id"), null).get();

            if (!saved.getString("ip").equals("192.0.2.101")) {
                throw new IllegalStateException("ip provided to Item apon saving and getting are not what was expected.");
            }

            Optional<Trait> wifiMacOptional = traitService.getTraitByName("Mac");
            structureService.addTraitToStructure(structure.getId(), "wifiMac", wifiMacOptional.get());

            saved.put("wifiMac", "aaaaddddrrrr");

            itemService.upsertItem(structure.getId(), saved, null);

            saved = itemService.getItemById(structure.getId(), saved.getString("id"), null).get();

            if (!saved.getString("wifiMac").equals("aaaaddddrrrr")) {
                throw new IllegalStateException("Data provided to Item apon saving and getting");
            }


        } catch (Exception e) {
            throw e;
        } finally {
            itemService.delete(structure.getId(), saved.getString("id"), null);
            Thread.sleep(1000);
            structureService.delete(structure.getId());
        }


    }

    @Test
    public void upsertItemThenPerformPartialUpdate() throws Exception {

        Structure structure = structureTestHelper.getSimpleItemStructure();

        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.put("ip", "192.0.2.101");
        obj.put("mac", "111111111111");

        TypeCheckMap saved = itemService.upsertItem(structure.getId(), obj, null);

        try {
            Thread.sleep(1000);// give time for ES to flush the new item

            saved = itemService.getItemById(structure.getId(), saved.getString("id"), null).get();

            if (!saved.getString("mac").equals("111111111111")) {
                throw new IllegalStateException("mac provided to Item apon saving and getting are not what was expected.");
            }


            TypeCheckMap partial = new TypeCheckMap();
            partial.put("id", saved.getString("id"));// required to update
            partial.put("mac", "aaaaddddrrrr");
            partial.put("ip", "192.0.2.101");

            itemService.upsertItem(structure.getId(), partial, null);

            TypeCheckMap updated = itemService.getItemById(structure.getId(), saved.getString("id"), null).get();

            if (!updated.getString("mac").equals("aaaaddddrrrr")) {
                throw new IllegalStateException("mac provided to Item apon saving and getting are not what we expected from the updated.");
            }

        } catch (Exception e) {
            throw e;
        } finally {
            itemService.delete(structure.getId(), saved.getString("id"), null);
            Thread.sleep(1000);
            structureService.delete(structure.getId());
        }

    }
}
