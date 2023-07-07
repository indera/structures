// @ts-ignore for some reason intellij is complaining about this even though esModuleInterop is enabled
import path from 'node:path'
import {v2 as compose} from 'docker-compose'
import {Continuum} from '@kinotic/continuum-client'
import {Structure} from '../src/api/domain/Structure.js'
import {
    AutoGeneratedIdDecorator,
    EntityDecorator, IntC3Type,
    MultiTenancyType,
    ObjectC3Type,
    StringC3Type
} from '@kinotic/continuum-idl'
import {Structures} from '../src/index.js'
import {Person} from './domain/Person.js'

const composeFilePath = '../../'

export async function initStructuresServer(): Promise<void> {
    try {
        const resolvedPath = path.resolve(composeFilePath)

        await compose.pullAll({cwd: resolvedPath, log: true})

        await compose.upAll({
            cwd: resolvedPath,
            log: true
        })

        await Continuum.connect('ws://127.0.0.1:58503/v1', 'admin', 'structures')
        console.log('Connected to continuum')
    } catch (e) {
        console.error(e)
        throw e
    }
}

export async function shutdownStructuresServer(): Promise<void> {
    try {
        await compose.down({cwd: path.resolve(composeFilePath), log: true})
    } catch (e) {
        console.error(e)
        throw e
    }
}

export function createPersonSchema(): ObjectC3Type {
    const ret = new ObjectC3Type()
    ret.type = 'object'
    ret.addDecorator(new EntityDecorator().withMultiTenancyType(MultiTenancyType.SHARED))
    ret.addProperty('id', new StringC3Type().addDecorator(new AutoGeneratedIdDecorator()))
    ret.addProperty('firstName', new StringC3Type())
    ret.addProperty('lastName', new StringC3Type())
    ret.addProperty('age', new IntC3Type())

    const address = new ObjectC3Type()
    address.type = 'object'
    address.addProperty('street', new StringC3Type())
    address.addProperty('city', new StringC3Type())
    address.addProperty('state', new StringC3Type())
    address.addProperty('zip', new StringC3Type())

    ret.addProperty('address', address)

    return ret
}

export async function createPersonStructureIfNotExist(suffix: string): Promise<Structure>{
    const structureId = 'org.kinotic.sample.person' + suffix
    let structure = await Structures.getStructureService().findById(structureId)
    if(structure == null){
        structure = await createPersonStructure(suffix)
    }
    return structure
}

export async function createPersonStructure(suffix: string): Promise<Structure>{
    const personStructure = new Structure('org.kinotic.sample',
        'Person' + suffix,
        createPersonSchema(),
        'Tracks people that are going to mars')

    await Structures.getNamespaceService().createNamespaceIfNotExist('org.kinotic.sample', 'Sample Data Namespace')

    const savedStructure = await Structures.getStructureService().create(personStructure)

    await Structures.getStructureService().publish(savedStructure.id)

    return savedStructure
}

export async function deleteStructure(structureId: string): Promise<void>{
    await Structures.getStructureService().unPublish(structureId)
    await Structures.getStructureService().deleteById(structureId)
}

export function createTestPeople(numberToCreate: number): Person[] {
    const ret: Person[] = []
    for (let i = 0; i < numberToCreate; i++) {
        ret.push(createTestPerson())
    }
    return ret
}

export function createTestPerson(): Person {
    const ret = new Person()
    ret.firstName = 'John'
    ret.lastName = 'Doe'
    ret.age = 42
    ret.address = {
        street: '123 Main St',
        city: 'Anytown',
        state: 'CA',
        zip: '12345'
    }
    return ret
}

export function generateRandomString(length){
    let result = ''
    const characters =
              'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    const charactersLength = characters.length
    for (let i = 0; i < length; i++) {
        result += characters.charAt(Math.floor(Math.random() * charactersLength))
    }
    return result
}

/**
 * Logs the failure of a promise and then rethrows the error
 * @param promise to log failure of
 * @param message to log
 */
export async function logFailure<T>(promise: Promise<T>, message: string): Promise<T> {
    try {
        return await promise
    } catch (e) {
        console.error(message, e)
        throw e
    }
}
