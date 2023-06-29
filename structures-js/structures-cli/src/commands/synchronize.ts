import {Args, Command, Flags} from '@oclif/core'
import path from 'node:path'
import {ClassDeclaration, Project} from 'ts-morph'
import {createConversionContext} from '../internal/converter/IConversionContext.js'
import {TypescriptConverterStrategy} from '../internal/converter/typescript/TypescriptConverterStrategy.js'
import {TypescriptConversionState} from '../internal/converter/typescript/TypescriptConversionState.js'
import {C3Type, EntityDecorator, MultiTenancyType} from '@kinotic/continuum-idl'

export default class Synchronize extends Command {
  static description = 'Synchronize the local Entity definitions with the Structures Server'

  static examples = [
    `$ structures synchronize my.namespace --entities path/to/entities`,
  ]

  static flags = {
    entities: Flags.string({char: 'e', description: 'Path to the directory containing the Entity definitions', required: true}),
  }

  static args = {
    namespace: Args.string({description: 'The namespace the Entities belong to', required: true}),
  }

  async run(): Promise<void> {
    const {args, flags} = await this.parse(Synchronize)
    const entities: C3Type[] = []

    const project = new Project({ // TODO: make sure there is a tsconfig.json in the entities directory
      tsConfigFilePath: path.resolve('tsconfig.json')
    })

    project.enableLogging(true)
    const entitiesPath = path.resolve(flags.entities)
    project.addSourceFilesAtPaths(entitiesPath + '/*.ts')

    const sourceFiles = project.getSourceFiles(entitiesPath +'/*.ts')
    for (const sourceFile of sourceFiles) {

      const conversionContext = createConversionContext(new TypescriptConverterStrategy(new TypescriptConversionState(args.namespace, project), this))

      const exportedDeclarations = sourceFile.getExportedDeclarations()
      exportedDeclarations.forEach((exportedDeclarationEntries, name) => {
        exportedDeclarationEntries.forEach((exportedDeclaration) => {
          if (ClassDeclaration.isClassDeclaration(exportedDeclaration)) {
            // We only convert entities TODO: see if we can insure this is actually a structures entity
            const decorator = exportedDeclaration.getDecorator('Entity')
            if(decorator != null) {

              let c3Type: C3Type | null = null
              try {
                c3Type = conversionContext.convert(exportedDeclaration)
              } catch (e) {
              } // We ignore this error since the converter will print any errors

              if (c3Type != null) {

                const entityDecorator = new EntityDecorator()
                if (decorator.getArguments().length == 1) {
                  const argument = decorator.getArguments()[0]
                  if (argument?.getText() == 'MultiTenancyType.SHARED') {
                    entityDecorator.multiTenancyType = MultiTenancyType.SHARED
                  } else if (argument?.getText() == 'MultiTenancyType.NONE') {
                    entityDecorator.multiTenancyType = MultiTenancyType.NONE
                  } else {
                    throw new Error(`Unsupported MultiTenancyType ${argument?.getText()}`)
                  }
                }
                c3Type.addDecorator(entityDecorator)

                entities.push(c3Type)
              }else{
                this.error(`Could not convert ${name} to a C3Type. The process will terminate.`)
                return;
              }
            }
          }
        })
      })
    }

    // save the c3types to the local filesystem
    const json = JSON.stringify(entities, null, 2)
    this.log("JSON:")
    this.log(json)
  }


}
