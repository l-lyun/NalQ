const fs = require('node:fs')
const path = require('node:path')
const { createRequire } = require('node:module')
const ts = require('typescript')

module.exports = function fixture(stubs = {}) {
  const root = path.resolve(__dirname, '../../src')
  const cache = new Map()
  function load(relative) {
    const filename = path.isAbsolute(relative) ? relative : path.join(root, relative)
    if (cache.has(filename)) return cache.get(filename).exports
    const module = { exports: {} }
    cache.set(filename, module)
    const localRequire = createRequire(filename)
    const source = fs.readFileSync(filename, 'utf8').replaceAll('import.meta.env', '({})')
    const code = ts.transpileModule(source, { compilerOptions: {
      module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022,
      esModuleInterop: true,
    } }).outputText
    const requireFixture = (name) => {
      if (Object.hasOwn(stubs, name)) return stubs[name]
      const resolved = name.startsWith('@/') ? path.join(root, name.slice(2))
        : name.startsWith('.') ? path.resolve(path.dirname(filename), name) : null
      if (resolved && fs.existsSync(`${resolved}.ts`)) return load(`${resolved}.ts`)
      return localRequire(name)
    }
    new Function('require', 'module', 'exports', code)(requireFixture, module, module.exports)
    return module.exports
  }
  return { load }
}
