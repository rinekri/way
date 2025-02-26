package ru.kode.way.gradle

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec

internal fun buildSchemaFileSpec(parseResult: SchemaParseResult, config: CodeGenConfig): FileSpec {
  val schemaClassName = schemaClassName(parseResult, config)
  val schemaFileSpec = FileSpec.builder(
    parseResult.customPackage ?: config.outputPackageName,
    parseResult.customSchemaFileName ?: schemaClassName
  )
  val regionRoots = buildRegionRoots(parseResult.adjacencyList)
  val constructorParameters = buildConstructorParameters(parseResult.adjacencyList)
  val constructorProperties = constructorParameters.map {
    PropertySpec.builder(it.name, it.type, KModifier.PRIVATE)
      .initializer(it.name)
      .build()
  }

  fun buildSegmentId(node: Node): String {
    return buildSegmentId(parseResult.filePath, node)
  }

  val schemaTypeSpec = TypeSpec.classBuilder(name = schemaClassName)
    .apply {
      if (constructorParameters.isNotEmpty()) {
        primaryConstructor(FunSpec.constructorBuilder().addParameters(constructorParameters).build())
        addProperties(constructorProperties)
      }
    }
    .addSuperinterface(libraryClassName("Schema"))
    .addProperty(
      buildRootSegmentProperty(parseResult.adjacencyList, ::buildSegmentId)
    )
    .addProperty(
      buildChildSchemasProperty(parseResult.adjacencyList, ::buildSegmentId)
    )
    .addProperty(
      buildRegionsPropertySpec(regionRoots, parseResult.adjacencyList, ::buildSegmentId)
    )
    .addFunction(
      buildSchemaTargetsSpec(parseResult.adjacencyList, ::buildSegmentId)
    )
    .addFunction(
      buildSchemaNodeTypeSpec(parseResult.adjacencyList, ::buildSegmentId)
    )
  return schemaFileSpec.addType(schemaTypeSpec.build()).build()
}

private fun buildRegionsPropertySpec(
  regionRoots: List<Node>,
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String
): PropertySpec {
  return PropertySpec.Companion.builder(
    "regions",
    LIST.parameterizedBy(libraryClassName("RegionId")),
    KModifier.OVERRIDE
  )
    .initializer(
      CodeBlock.builder()
        .add("listOf(")
        .apply {
          regionRoots.forEachIndexed { index, r ->
            add(
              "%T(%L)",
              libraryClassName("RegionId"),
              buildPathConstructorCall(reversedParents(r, adjacencyList), buildSegmentId)
            )
            if (index != regionRoots.lastIndex) {
              add(", ")
            }
          }
        }
        .add(")")
        .build()
    )
    .build()
}

private fun buildRootSegmentProperty(
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String
): PropertySpec {
  val rootNode = adjacencyList.findRootNode()
  return PropertySpec
    .builder("rootSegment", libraryClassName("Segment"), KModifier.OVERRIDE)
    .initializer(
      CodeBlock.builder()
        .add(
          "%T(%S, %T(%S))",
          libraryClassName("Segment"),
          rootNode.id,
          libraryClassName("SegmentId"),
          buildSegmentId(rootNode)
        )
        .build()
    )
    .build()
}

private fun buildChildSchemasProperty(
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String
): PropertySpec {
  val rootNode = adjacencyList.findRootNode()
  val importedFlowNodes = mutableListOf<Node>()
  dfs(adjacencyList, rootNode) { node ->
    when (node) {
      is Node.Flow.Imported -> importedFlowNodes.add(node)
      is Node.Flow.Local,
      is Node.Flow.LocalParallel,
      is Node.Screen -> Unit
    }
  }
  if (importedFlowNodes.isEmpty()) {
    return PropertySpec
      .builder(
        "childSchemas",
        MAP.parameterizedBy(
          libraryClassName("SegmentId"),
          libraryClassName("Schema")
        ),
        KModifier.OVERRIDE
      )
      .initializer("emptyMap()")
      .build()
  } else {
    return PropertySpec
      .builder(
        "childSchemas",
        MAP.parameterizedBy(
          libraryClassName("SegmentId"),
          libraryClassName("Schema")
        ),
        KModifier.OVERRIDE
      )
      .initializer(
        CodeBlock.builder()
          .add("mapOf(")
          .apply {
            importedFlowNodes.forEachIndexed { index, node ->
              add(
                "%T(%S)·to·%L",
                libraryClassName("SegmentId"),
                buildSegmentId(node),
                schemaConstructorPropertyName(node)
              )
              if (index != importedFlowNodes.lastIndex) {
                add(", ")
              }
            }
          }
          .add(")")
          .build()
      )
      .build()
  }
}

private fun buildRegionRoots(adjacencyList: AdjacencyList): List<Node> {
  val regionRoots = mutableListOf<Node>()
  adjacencyList.forEach { (node, children) ->
    if (node is Node.Flow.LocalParallel) {
      regionRoots.addAll(children)
    }
  }
  if (regionRoots.isEmpty()) {
    regionRoots.add(adjacencyList.findRootNode())
  }
  return regionRoots
}

private fun buildConstructorParameters(adjacencyList: AdjacencyList): List<ParameterSpec> {
  val parameters = mutableListOf<ParameterSpec>()
  adjacencyList.forEach { (node, _) ->
    if (node is Node.Flow.Imported) {
      parameters.add(
        ParameterSpec.builder(schemaConstructorPropertyName(node), libraryClassName("Schema"))
          .build()
      )
    }
  }
  return parameters
}

private fun buildSchemaTargetsSpec(
  adjacencyList: AdjacencyList,
  buildSegmentId: (Node) -> String
): FunSpec {
  return FunSpec.builder("target")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter("regionId", libraryClassName("RegionId"))
    .addParameter("segmentId", libraryClassName("SegmentId"))
    .addParameter("rootSegmentAlias", libraryClassName("Segment").copy(nullable = true))
    .returns(libraryClassName("Path").copy(nullable = true))
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return·when·(regionId)·{")
        .apply {
          buildRegionRoots(adjacencyList).forEachIndexed { regionRootIndex, regionRoot ->
            beginControlFlow("regions[$regionRootIndex] -> {")
            addStatement(
              "val rootSegment = rootSegmentAlias ?: %T(%S, %T(%S))",
              libraryClassName("Segment"),
              regionRoot.id,
              libraryClassName("SegmentId"),
              buildSegmentId(regionRoot)
            )
            beginControlFlow("when(segmentId.value) {")
            // TODO @AdjacencyMatrix
            //  not very efficient: running DFS and then for each node inspecting all adjacency list to find parent
            //  adjacency matrix would allow to find parent nodes more easily.
            //  This stuff is going on in many places during codegen, search for them if will be optimizing
            dfs(adjacencyList, regionRoot) { node ->
              if (node.id == regionRoot.id) {
                addStatement(
                  "rootSegment.id.value -> %T(rootSegment)",
                  libraryClassName("Path"),
                )
              } else {
                addStatement(
                  "%S -> %T(rootSegment, %L)",
                  buildSegmentId(node),
                  libraryClassName("Path"),
                  buildSegmentArgumentList(reversedParents(node, adjacencyList).drop(1), buildSegmentId)
                )
              }
            }
            addStatement("else -> null")
            endControlFlow() // when (segment.name)
            endControlFlow() // regions[index] -> {
          }
        }
        .beginControlFlow("else -> {")
        .addStatement("error(%P)", "unknown regionId=\$regionId")
        .endControlFlow()
        .endControlFlow() // return when
        .build()
    )
    .build()
}

private fun buildSchemaNodeTypeSpec(adjacencyList: AdjacencyList, buildSegmentId: (Node) -> String): FunSpec {
  return FunSpec.builder("nodeType")
    .addModifiers(KModifier.OVERRIDE)
    .addParameter("regionId", libraryClassName("RegionId"))
    .addParameter("path", libraryClassName("Path"))
    .addParameter("rootSegmentAlias", libraryClassName("Segment").copy(nullable = true))
    .returns(libraryClassName("Schema").nestedClass("NodeType"))
    .addCode(
      CodeBlock.builder()
        .beginControlFlow("return when (regionId) {")
        .apply {
          buildRegionRoots(adjacencyList).forEachIndexed { regionRootIndex, regionRoot ->
            beginControlFlow("regions[$regionRootIndex] -> {")
            addStatement(
              "val rootSegment = rootSegmentAlias ?: %T(%S, %T(%S))",
              libraryClassName("Segment"),
              regionRoot.id,
              libraryClassName("SegmentId"),
              buildSegmentId(regionRoot)
            )
            beginControlFlow("when {")
            dfs(adjacencyList, regionRoot) { node ->
              when (node) {
                is Node.Flow.Local, is Node.Flow.Imported -> {
                  if (node.id == regionRoot.id) {
                    addStatement(
                      "path == %T(rootSegment) -> %T.NodeType.Flow",
                      libraryClassName("Path"),
                      libraryClassName("Schema")
                    )
                  } else {
                    addStatement(
                      "path == %T(rootSegment, %L) -> %T.NodeType.Flow",
                      libraryClassName("Path"),
                      buildSegmentArgumentList(reversedParents(node, adjacencyList).drop(1), buildSegmentId),
                      libraryClassName("Schema")
                    )
                  }
                }

                is Node.Flow.LocalParallel -> {
                  addStatement(
                    "path == %T(rootSegment, %L) -> %T.NodeType.Parallel",
                    libraryClassName("Path"),
                    buildSegmentArgumentList(reversedParents(node, adjacencyList).drop(1), buildSegmentId),
                    libraryClassName("Schema")
                  )
                }

                is Node.Screen -> {
                  addStatement(
                    "path == %T(rootSegment, %L) -> %T.NodeType.Screen",
                    libraryClassName("Path"),
                    buildSegmentArgumentList(reversedParents(node, adjacencyList).drop(1), buildSegmentId),
                    libraryClassName("Schema")
                  )
                }
              }
            }
            beginControlFlow("else -> {")
            addStatement("error(%P)", "internal error: no nodeType for path=\$path")
            endControlFlow() // else -> {
            endControlFlow() // when {
            endControlFlow() // regions[i] -> {
          }
          beginControlFlow("else -> {")
          addStatement("error(%P)", "unknown regionId=\$regionId")
          endControlFlow()
        }
        .endControlFlow() // return when (regionId) {
        .build()
    )
    .build()
}

private fun schemaConstructorPropertyName(node: Node) = "${node.id}Schema"

internal fun schemaClassName(parseResult: SchemaParseResult, config: CodeGenConfig): String {
  return parseResult.graphId?.let { "${it}Schema" } ?: config.outputSchemaClassName
}

private fun reversedParents(
  node: Node,
  adjacencyList: AdjacencyList,
): List<Node> {
  return adjacencyList
    .findAllParents(node, includeThis = true)
    .reversed()
}
