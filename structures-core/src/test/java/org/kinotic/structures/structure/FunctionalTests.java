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

package org.kinotic.structures.structure;

import org.elasticsearch.ElasticsearchStatusException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kinotic.structures.ElasticsearchTestBase;
import org.kinotic.structures.api.domain.Structure;
import org.kinotic.structures.api.domain.Trait;
import org.kinotic.structures.api.domain.TypeCheckMap;
import org.kinotic.structures.api.services.TraitService;
import org.kinotic.structures.internal.api.services.ItemServiceInternal;
import org.kinotic.structures.internal.api.services.StructureServiceInternal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

@ExtendWith(SpringExtension.class)
@SpringBootTest
public class FunctionalTests extends ElasticsearchTestBase {

    @Autowired
    private TraitService traitService;
    @Autowired
    private StructureServiceInternal structureService;
    @Autowired
    private ItemServiceInternal itemService;


    @Test
    public void tryAddAdditionalFieldOutsideSchema() throws Exception {
        Assertions.assertThrows(ElasticsearchStatusException.class, () -> {
            Structure structure = new Structure();
            structure.setName("Computer7-" + System.currentTimeMillis());
            structure.setNamespace("some_other_org_");
            structure.setDescription("Defines the Computer Device properties");

            Optional<Trait> vpnIpOptional = traitService.getTraitByName("VpnIp");
            Optional<Trait> ipOptional = traitService.getTraitByName("Ip");

            structure.getTraits().put("vpnIp", vpnIpOptional.get());
            structure.getTraits().put("ip", ipOptional.get());
            // should also get createdTime, updateTime, and deleted by default

            structure = structureService.save(structure);

            try {

                structureService.publish(structure.getId());

                // now we can create an item with the above fields
                TypeCheckMap obj = new TypeCheckMap();
                obj.put("vpnIp", "172.16.0.11");
                obj.put("ip", "192.0.2.11");
                obj.put("mac", "aaaaaaaaaaa1");
                itemService.upsertItem(structure.getId(), obj, null);

            } catch (ElasticsearchStatusException e) {
                throw e;
            } finally {
                structureService.delete(structure.getId());
            }
        });
    }

    @Test
    public void addTraitAfterPublishedAndNewItemAddedThenUpdateItem() throws Exception {
        Structure structure = new Structure();
        structure.setName("Computer8-" + System.currentTimeMillis());
        structure.setNamespace("some_other_org_");
        structure.setDescription("Defines the Computer Device properties");


        Optional<Trait> vpnIpOptional = traitService.getTraitByName("VpnIp");
        Optional<Trait> ipOptional = traitService.getTraitByName("Ip");
        Optional<Trait> macOptional = traitService.getTraitByName("Mac");

        structure.getTraits().put("vpnIp", vpnIpOptional.get());
        structure.getTraits().put("ip", ipOptional.get());
        // should also get createdTime, updateTime, and deleted by default

        structure = structureService.save(structure);
        structureService.publish(structure.getId());


        // now we can create an item with the above fields
        TypeCheckMap obj = new TypeCheckMap();
        obj.amend("ip", "192.0.2.11");
        obj.amend("vpnIp","10.99.99.9");
        TypeCheckMap savedJsonItem = itemService.upsertItem(structure.getId(), obj, null);
        TypeCheckMap item = null;

        try {
            structureService.addTraitToStructure(structure.getId(), "mac", macOptional.get());

            savedJsonItem.put("mac", "aaaaaaaaaaa1");

            itemService.upsertItem(structure.getId(), savedJsonItem, null);

            item = itemService.getItemById(structure.getId(), savedJsonItem.getString("id"), null).get();

            if (!item.has("mac")) {
                throw new IllegalStateException("After save of an Item, new Trait field added, mac, does not exist after pulling from ES.");
            }


            if (!item.has("ip")) {
                throw new IllegalStateException("After save of an Item, field 'ip' does not exist after pulling from ES.");
            }

        } catch (Exception e) {
            throw e;
        } finally {
            if (item != null) {
                itemService.delete(structure.getId(), item.getString("id"), null);
            } else {
                itemService.delete(structure.getId(), savedJsonItem.getString("id"), null);
            }

            Thread.sleep(1000);
            structureService.delete(structure.getId());
        }

    }

    @Test
    public void addTraitAfterPublishedAndNewItems() throws Exception {
        Structure structure = new Structure();
        structure.setName("Computer9-"+System.currentTimeMillis());
        structure.setNamespace("some_other_org_");
        structure.setDescription("Defines the Computer Device properties");


        Optional<Trait> vpnIpOptional = traitService.getTraitByName("VpnIp");
        Optional<Trait> ipOptional = traitService.getTraitByName("Ip");
        Optional<Trait> macOptional = traitService.getTraitByName("Mac");

        structure.getTraits().put("vpnIp", vpnIpOptional.get());
        structure.getTraits().put("ip", ipOptional.get());
        // should also get createdTime, updateTime, and deleted by default

        structure = structureService.save(structure);
        structureService.publish(structure.getId());


        // now we can create an item with the above fields
        TypeCheckMap firstItem = new TypeCheckMap();
        firstItem.amend("ip","192.0.2.11");
        firstItem.amend("vpnIp","10.99.99.9");
        TypeCheckMap savedJsonItem = itemService.upsertItem(structure.getId(), firstItem, null);
        TypeCheckMap item = null;

        try{
            structureService.addTraitToStructure(structure.getId(), "mac", macOptional.get());

            TypeCheckMap secondItem = new TypeCheckMap();
            secondItem.amend("ip","192.0.2.12");
            secondItem.amend("vpnIp","10.99.99.10");
            secondItem.amend("mac", "aaaaaaaaaaa2");

            item = itemService.upsertItem(structure.getId(), secondItem, null);

            TypeCheckMap freshFromDb = itemService.getItemById(structure.getId(), item.getString("id"), null).get();

            if(!freshFromDb.has("mac")){
                throw new IllegalStateException("After save of an Item, new Trait field added, mac, does not exist after pulling from ES.");
            }

            if(!freshFromDb.has("ip")){
                throw new IllegalStateException("After save of an Item, field 'ip' does not exist after pulling from ES.");
            }
        }catch(Exception e){
            throw e;
        }finally{
            if(item != null){
                itemService.delete(structure.getId(), item.getString("id"), null);
            }
            if(savedJsonItem != null){
                itemService.delete(structure.getId(), savedJsonItem.getString("id"), null);
            }
            Thread.sleep(1000);
            structureService.delete(structure.getId());
        }
    }
}
