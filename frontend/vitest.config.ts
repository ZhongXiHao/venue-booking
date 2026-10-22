import { fileURLToPath } from 'node:url'
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

export default defineConfig((configEnv) =>
  mergeConfig(
    viteConfig(configEnv),
    {
      test: {
        environment: 'jsdom',
        exclude: ['e2e/**'],
        root: fileURLToPath(new URL('./', import.meta.url)),
      },
    },
  ),
)