import js from '@eslint/js';
import globals from 'globals';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import importX from 'eslint-plugin-import-x';

/** Zasady składniowe obowiązujące w całym src/ (wyjątki w blokach poniżej). */
const restrictedSyntax = [
  {
    selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
    message: 'dangerouslySetInnerHTML jest zabroniony (ryzyko XSS).',
  },
  {
    selector: "NewExpression[callee.name='Date']",
    message: 'Operacje na czasie tylko przez shared/lib/datetime.',
  },
  {
    selector: "CallExpression[callee.object.name='Date'][callee.property.name='now']",
    message: 'Date.now() tylko w shared/lib/datetime.',
  },
  {
    selector: "MemberExpression[object.name='Intl'][property.name='DateTimeFormat']",
    message: 'Intl.DateTimeFormat tylko w shared/lib/datetime.',
  },
  {
    selector: 'CallExpression[callee.property.name=/^toLocale(Date|Time)?String$/]',
    message: 'toLocale*String tylko w shared/lib/datetime.',
  },
  {
    selector: "JSXExpressionContainer > MemberExpression[property.name='detail']",
    message: 'Nie wypisuj error.detail w JSX. Użyj describeError() z shared/lib/errors.',
  },
];

export default tseslint.config(
  {
    ignores: [
      'dist',
      'coverage',
      'playwright-report',
      'test-results',
      'node_modules',
      'src/shared/api/generated',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.strictTypeChecked,
  {
    languageOptions: {
      ecmaVersion: 2023,
      globals: { ...globals.browser },
      parserOptions: {
        projectService: { allowDefaultProject: ['*.js', 'vite.config.ts'] },
        tsconfigRootDir: import.meta.dirname,
      },
    },
    plugins: { 'react-hooks': reactHooks, 'jsx-a11y': jsxA11y, 'import-x': importX },
    settings: {
      'import-x/resolver': { typescript: { project: './tsconfig.json' } },
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      ...jsxA11y.flatConfigs.recommended.rules,
      'import-x/no-cycle': 'error',
      'import-x/order': ['error', { 'newlines-between': 'never' }],
      'no-console': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' },
      ],
      'no-restricted-syntax': ['error', ...restrictedSyntax],
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/ban-ts-comment': [
        'error',
        {
          'ts-ignore': true,
          'ts-expect-error': 'allow-with-description',
          minimumDescriptionLength: 10,
        },
      ],
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/restrict-template-expressions': ['error', { allowNumber: true }],
      // Granice funkcji: import z innej funkcji tylko przez jej index.ts
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ['@/features/*/*'],
              message: 'Importuj z innej funkcji wyłącznie przez @/features/<nazwa> (index.ts).',
            },
          ],
        },
      ],
    },
  },
  // Wyjątki: moduł czasu, testy, narzędzia testowe
  {
    files: ['src/shared/lib/datetime/**', 'src/**/*.test.{ts,tsx}', 'src/shared/test/**', 'e2e/**'],
    rules: {
      'no-restricted-syntax': [
        'error',
        ...restrictedSyntax.filter(
          (r) => !r.selector.includes('Date') && !r.selector.includes('toLocale'),
        ),
      ],
    },
  },
  {
    files: ['src/shared/lib/errors/**', 'src/**/*.test.{ts,tsx}'],
    rules: {
      'no-restricted-syntax': [
        'error',
        ...restrictedSyntax.filter((r) => !r.selector.includes('detail')),
      ],
    },
  },
  {
    files: ['src/**/*.test.{ts,tsx}', 'src/shared/test/**', 'e2e/**'],
    rules: { 'no-restricted-syntax': 'off', 'no-console': 'off' },
  },
  // Dane medyczne i pacjenci: brak lokalnego magazynu (obowiązuje od 1b)
  {
    files: ['src/features/records/**', 'src/features/patients/**'],
    rules: {
      'no-restricted-globals': [
        'error',
        { name: 'localStorage', message: 'Dane medyczne nie trafiają do magazynu przeglądarki.' },
        { name: 'sessionStorage', message: 'Dane medyczne nie trafiają do magazynu przeglądarki.' },
      ],
    },
  },
  // Skrypty Node i pliki konfiguracyjne
  {
    files: ['scripts/**', '*.config.{js,ts}', 'vite.config.ts', 'eslint.config.js'],
    languageOptions: { globals: { ...globals.node } },
    rules: { 'no-console': 'off', 'no-restricted-syntax': 'off' },
  },
  {
    files: ['**/*.{js,mjs}'],
    extends: [tseslint.configs.disableTypeChecked],
  },
);
