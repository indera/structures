import {C3Decorator} from '@kinotic/continuum-idl'

/**
 * Signifies the ID field of an entity, The ID will be auto generated.
 */
export class AutoGeneratedIdDecorator extends C3Decorator {

    constructor() {
        super()
        this.type = 'AutoGeneratedId'
    }
}
