import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist', 'src/api/schema.d.ts', 'e2e/screenshots', 'test-results', 'playwright-report'] },
  {
    files: ['**/*.{ts,tsx}'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: { ecmaVersion: 2022, globals: { ...globals.browser, ...globals.node } },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      // Colours, fonts, radii and spacing come from the CSS variables in design/tokens.css.
      'no-restricted-syntax': [
        'error',
        { selector: "Literal[value=/#[0-9a-fA-F]{3,8}\\b/]", message: 'No raw hex colours. Use a CSS variable from design/tokens.css.' },
      ],
    },
  },
)
