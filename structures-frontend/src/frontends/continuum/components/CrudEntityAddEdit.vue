<template>
    <v-dialog v-model="dialog" fullscreen scrollable hide-overlay transition="dialog-bottom-transition" >
        <v-card>
            <div>
                <v-toolbar dark color="primary" style="border-top-right-radius:0;
                                                       border-top-left-radius:0">
                    <v-btn icon dark @click="close">
                        <v-icon>{{closeIcon}}</v-icon>
                    </v-btn>
                    <v-toolbar-title>{{title}}</v-toolbar-title>
                    <v-spacer></v-spacer>
                    <v-toolbar-items>
                        <v-btn @click="save"
                               :disabled="!valid"
                               dark
                               text>
                            Save
                        </v-btn>
                    </v-toolbar-items>
                </v-toolbar>
            </div>
            <v-list>
                <v-subheader>Basic Information</v-subheader>
                <v-list-item>
                    <v-list-item-content>
                        <v-form ref="form"
                                v-model="valid"
                                lazy-validation>

                            <v-text-field v-model="syncedEntity.identity"
                                          :disabled="editing"
                                          :rules="identityRules"
                                          :label="identityLabel"
                                          required>
                            </v-text-field>

                            <slot name="basic-info" :entity="syncedEntity">
                            </slot>

                        </v-form>
                    </v-list-item-content>
                </v-list-item>
            </v-list>

            <slot name="additional-info" :entity="syncedEntity">
            </slot>

            <v-overlay :value="loading">
                <v-progress-circular indeterminate size="64"></v-progress-circular>
            </v-overlay>
        </v-card>
    </v-dialog>
</template>

<script lang="ts">
import { PropType } from 'vue'
import { Component, Prop, Vue, PropSync, Emit } from 'vue-property-decorator'
import { inject } from 'inversify-props'
import { ICrudServiceProxy, ICrudServiceProxyFactory, Identifiable } from '@kinotic-foundation/continuum-js'
import { mdiClose } from '@mdi/js'

// Function that takes an input value as an argument and return either true / false or a string with an error message
type RuleValidator = (value: any) => string | boolean

@Component({
    components: { }
})
export default class CrudEntityAddEdit extends Vue {

    @Prop({type: String, required: true})
    public crudServiceIdentifier!: string

    @Prop({type: String, required: true})
    public title!: string

    @Prop({type: String, required: false, default: null})
    public identity!: string | null

    @Prop({type: String, required: false, default: 'Name*'})
    public identityLabel!: string

    @PropSync('entity', {type: Object as PropType<Identifiable<string>>, required: false, default: { identity: '' }})
    public syncedEntity!: Identifiable<string>

    /**
     * Services
     */
    @inject()
    private crudServiceProxyFactory!: ICrudServiceProxyFactory
    private crudServiceProxy!: ICrudServiceProxy<any>

    /**
     * Icons
     */
    private closeIcon: string = mdiClose

    /**
     * Data Vars
     */
    private editing: boolean = false
    private dialog: boolean = false
    private valid: boolean = true
    private loading: boolean = false
    private identityRules: RuleValidator[] = []


    constructor() {
        super()
    }

    // Lifecycle hooks
    public mounted() {
        this.identityRules = [
            ( v ) => !!v || this.identityLabel + ' is required'
        ]

        this.crudServiceProxy = this.crudServiceProxyFactory.crudServiceProxy(this.crudServiceIdentifier)

        if (this.identity !== null) {
            this.editing = true
            this.loading = true

            this.crudServiceProxy.findByIdentity(this.identity).then((item: Identifiable<string>) => {
                this.syncedEntity = item

                this.afterLoad(item)

                this.loading = false
            }).catch((error) => {
                this.loading = false
                this.displayAlert(error.message)
            })
        }

        // We open this with a variable that way you will see the open animation.
        // If we merely set value="true" on the dialog it will flash open
        this.dialog = true
    }

    public close() {
        this.hideAlert()
        this.dialog = false
        this.$router.back()
    }

    public async save() {
        this.hideAlert()
        if ((this.$refs.form as Vue & { validate: () => boolean }).validate()) {
            this.loading = true

            this.beforeSave()

            // check if this was an edit or new item
            try {
                if (!this.editing) { // new item

                    await this.crudServiceProxy.create(this.syncedEntity)

                } else { // edit item

                    await this.crudServiceProxy.save(this.syncedEntity)

                }
                this.close()
            } catch (error: any) {
                this.displayAlert(error.message)
            }
            this.loading = false
        }
    }

    @Emit()
    public beforeSave(): Identifiable<string> {
        return this.syncedEntity
    }

    @Emit()
    public afterLoad(identifiable: Identifiable<string>): Identifiable<string> {
        return identifiable
    }

    private hideAlert() {
        (this.$notify as any as { close: (value: string) => void }).close('crudEntityAddEditAlert')
    }

    private displayAlert(text: string) {
        this.$notify({ group: 'alert', type: 'crudEntityAddEditAlert', text })
    }

}
</script>

<style scoped>

</style>
