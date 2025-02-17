// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.lang.UrlClassLoader
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2

internal class ClassPathXmlPathResolver(
  private val classLoader: ClassLoader,
  @JvmField val isRunningFromSources: Boolean,
) : PathResolver {
  override val isFlat: Boolean
    get() = true

  override fun loadXIncludeReference(
    readInto: RawPluginDescriptor,
    readContext: ReadModuleContext,
    dataLoader: DataLoader,
    base: String?,
    relativePath: String,
  ): Boolean {
    val path = PluginXmlPathResolver.toLoadPath(relativePath, base)
    val reader: XMLStreamReader2
    if (classLoader is UrlClassLoader) {
      reader = createNonCoalescingXmlStreamReader(classLoader.getResourceAsBytes(path, true) ?: return false, dataLoader.toString())
    }
    else {
      reader = createNonCoalescingXmlStreamReader(classLoader.getResourceAsStream(path) ?: return false, dataLoader.toString())
    }
    readModuleDescriptor(
      reader = reader,
      readContext = readContext,
      pathResolver = this,
      dataLoader = dataLoader,
      includeBase = PluginXmlPathResolver.getChildBase(base = base, relativePath = relativePath),
      readInto = readInto,
    )
    return true
  }

  override fun resolveModuleFile(
    readContext: ReadModuleContext,
    dataLoader: DataLoader,
    path: String,
    readInto: RawPluginDescriptor?,
  ): RawPluginDescriptor {
    val resource: ByteArray?
    if (classLoader is UrlClassLoader) {
      resource = classLoader.getResourceAsBytes(path, true)
    }
    else {
      classLoader.getResourceAsStream(path)?.let {
        return readModuleDescriptor(
          input = it,
          readContext = readContext,
          pathResolver = this,
          dataLoader = dataLoader,
          includeBase = null,
          readInto = readInto,
          locationSource = dataLoader.toString(),
        )
      }
      resource = null
    }

    if (resource == null) {
      val log = logger<ClassPathXmlPathResolver>()
      val moduleName = path.removeSuffix(".xml")
      if (isRunningFromSources && path.startsWith("intellij.") && dataLoader.emptyDescriptorIfCannotResolve) {
        log.trace("Cannot resolve $path (dataLoader=$dataLoader, classLoader=$classLoader). ")
        val descriptor = RawPluginDescriptor()
        descriptor.`package` = "unresolved.$moduleName"
        return descriptor
      }
      if (ProductLoadingStrategy.strategy.isOptionalProductModule(moduleName)) {
        //this check won't be needed when we are able to load optional modules directly from product-modules.xml
        log.debug("Skip module '$path' since its descriptor cannot be found and it's optional")
        return RawPluginDescriptor().apply { `package` = "unresolved.$moduleName" }
      }

      throw RuntimeException("Cannot resolve $path (dataLoader=$dataLoader, classLoader=$classLoader)")
    }

    return readModuleDescriptor(
      input = resource,
      readContext = readContext,
      pathResolver = this,
      dataLoader = dataLoader,
      includeBase = null,
      readInto = readInto,
      locationSource = dataLoader.toString(),
    )
  }

  override fun resolvePath(readContext: ReadModuleContext,
                           dataLoader: DataLoader,
                           relativePath: String,
                           readInto: RawPluginDescriptor?): RawPluginDescriptor? {
    val path = PluginXmlPathResolver.toLoadPath(relativePath)
    return readModuleDescriptor(
      reader = getXmlReader(classLoader = classLoader, path = path, dataLoader = dataLoader) ?: return null,
      readContext = readContext,
      pathResolver = this,
      dataLoader = dataLoader,
      includeBase = null,
      readInto = readInto,
    )
  }

  private fun getXmlReader(classLoader: ClassLoader, path: String, dataLoader: DataLoader): XMLStreamReader2? {
    if (classLoader is UrlClassLoader) {
      return createNonCoalescingXmlStreamReader(classLoader.getResourceAsBytes(path, true) ?: return null, dataLoader.toString())
    }
    else {
      return createNonCoalescingXmlStreamReader(classLoader.getResourceAsStream(path) ?: return null, dataLoader.toString())
    }
  }
}