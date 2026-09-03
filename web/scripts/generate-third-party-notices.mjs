import { promises as fs } from 'node:fs'
import path from 'node:path'

const webRoot = path.resolve(import.meta.dirname, '..')
const repositoryRoot = path.resolve(webRoot, '..')
const projectRoots = [webRoot, path.join(repositoryRoot, 'app')]
const readText = async (file) => (await fs.readFile(file, 'utf8'))
  .replace(/\r\n?/gu, '\n')
  .replace(/[\t ]+$/gmu, '')
const packages = new Map()
const queue = []
for (const projectRoot of projectRoots) {
  const manifest = JSON.parse(await fs.readFile(path.join(projectRoot, 'package.json'), 'utf8'))
  for (const name of Object.keys(manifest.dependencies ?? {})) {
    queue.push({ name, from: projectRoot, projectRoot })
  }
}
const mitTerms = (copyright) => `MIT License

Copyright (c) ${copyright}

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.`

async function packageJsonPath(name, from, projectRoot) {
  const candidates = [path.join(from, 'node_modules', ...name.split('/'), 'package.json')]
  for (let directory = from; directory !== path.dirname(directory); directory = path.dirname(directory)) {
    if (path.basename(directory) === 'node_modules') {
      candidates.push(path.join(directory, ...name.split('/'), 'package.json'))
    }
  }
  candidates.push(path.join(projectRoot, 'node_modules', ...name.split('/'), 'package.json'))
  for (const candidate of candidates) {
    try {
      await fs.access(candidate)
      return candidate
    } catch {
      // Try the workspace-level installation next.
    }
  }
  throw new Error(`Installed package metadata not found: ${name}`)
}

while (queue.length > 0) {
  const { name, from, projectRoot } = queue.shift()
  const metadataPath = await packageJsonPath(name, from, projectRoot)
  const packageDirectory = await fs.realpath(path.dirname(metadataPath))
  const metadata = JSON.parse(await fs.readFile(metadataPath, 'utf8'))
  const key = `${metadata.name}@${metadata.version}`
  if (packages.has(key)) continue
  packages.set(key, { metadata, packageDirectory })
  for (const dependency of Object.keys(metadata.dependencies ?? {})) {
    queue.push({ name: dependency, from: packageDirectory, projectRoot })
  }
}

const sections = []
for (const [key, { metadata, packageDirectory }] of [...packages].sort(([a], [b]) => a.localeCompare(b))) {
  const topLevelFiles = await fs.readdir(packageDirectory)
  const files = topLevelFiles
    .filter((file) => /^(license|copying|notice)(\..*)?$/iu.test(file))
    .map((file) => ({ label: file, file: path.join(packageDirectory, file) }))
  for (const directoryName of ['dist', 'licenses']) {
    const directory = path.join(packageDirectory, directoryName)
    try {
      for (const file of await fs.readdir(directory)) {
        if (/^(license|copying|notice)(\..*)?$/iu.test(file)) {
          files.push({ label: `${directoryName}/${file}`, file: path.join(directory, file) })
        }
      }
    } catch {
      // The package has no nested license directory.
    }
  }
  files.sort((a, b) => a.label.localeCompare(b.label))
  const repository = typeof metadata.repository === 'string'
    ? metadata.repository
    : metadata.repository?.url
  const source = metadata.homepage ?? repository ?? `https://www.npmjs.com/package/${metadata.name}`
  let license = typeof metadata.license === 'string'
    ? metadata.license
    : JSON.stringify(metadata.license ?? metadata.licenses ?? 'UNKNOWN')
  const texts = files.length > 0
    ? await Promise.all(files.map(async ({ label, file }) => `--- ${label} ---\n${await readText(file)}`))
    : license === 'MIT'
      ? [mitTerms(typeof metadata.author === 'string' ? metadata.author : metadata.author?.name ?? `${metadata.name} contributors`)]
      : metadata.name.startsWith('@seed-design/')
        ? [
            `--- SEED Design LICENSE ---\n${await readText(path.join(webRoot, 'src/pages/public-service/licenses/seed-license.txt'))}`,
            `--- SEED Design NOTICE ---\n${await readText(path.join(webRoot, 'src/pages/public-service/licenses/seed-notice.txt'))}`,
          ]
        : [`No license file was included in the installed package. SPDX/package license: ${license}`]
  if (metadata.name.startsWith('@seed-design/') && license === '"UNKNOWN"') license = 'Apache-2.0'
  sections.push(`${key}\nLicense: ${license}\nSource: ${source}\n\n${texts.join('\n\n')}`)
}

const output = [
  'NalQ Web and App Third-Party Notices',
  '',
  'Generated from the production dependency graphs in web/package.json and app/package.json.',
  'Package license files are reproduced below as shipped by each installed package.',
  '',
  ...sections.map((section) => `${'='.repeat(80)}\n${section}`),
  '',
].join('\n')

const outputPath = path.join(webRoot, 'src/pages/public-service/licenses/third-party-notices.txt')
if (process.argv.includes('--check')) {
  const current = await fs.readFile(outputPath, 'utf8')
  if (current !== output) throw new Error('Third-party notices are out of date. Run pnpm licenses:generate.')
} else {
  await fs.writeFile(outputPath, output, 'utf8')
}
