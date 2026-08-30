import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'PeSIT Wizard',
  description: 'A modern, fully open-source PeSIT E file-transfer node',
  lang: 'en-US',

  // Ignore localhost links used in development examples
  ignoreDeadLinks: [
    /^http:\/\/localhost/,
  ],

  head: [
    ['link', { rel: 'icon', href: '/favicon.ico' }]
  ],

  themeConfig: {
    logo: '/logo.svg',

    nav: [
      { text: 'Home', link: '/' },
      { text: 'Quick start', link: '/guide/quickstart' },
      { text: 'Guide', link: '/guide/' },
      { text: 'API', link: '/api/' },
      { text: 'GitHub', link: 'https://github.com/pesitwizard/pesitwizard-rs' }
    ],

    sidebar: {
      '/': [
        {
          text: 'Introduction',
          items: [
            { text: 'What is PeSIT Wizard?', link: '/guide/' },
            { text: 'Quick start', link: '/guide/quickstart' },
            { text: 'Architecture', link: '/guide/architecture' }
          ]
        },
        {
          text: 'Guide',
          items: [
            { text: 'The node', link: '/guide/server/installation' },
            { text: 'Configuration', link: '/guide/server/configuration' },
            { text: 'Partners & transfers', link: '/guide/client/usage' },
            { text: 'Storage connectors', link: '/guide/server/connectors' },
            { text: 'Certificates & PKI', link: '/guide/server/security' },
            { text: 'Clustering (NATS)', link: '/guide/server/clustering' },
            { text: 'Observability', link: '/guide/server/observability' }
          ]
        },
        {
          text: 'Operations',
          items: [
            { text: 'Deployment', link: '/guide/deployment' },
            { text: 'Connect:Express interop', link: '/guide/connect-express' },
            { text: 'Troubleshooting', link: '/guide/troubleshooting' }
          ]
        },
        {
          text: 'Reference',
          items: [
            { text: 'PeSIT E protocol', link: '/guide/reference/protocol' },
            { text: 'REST API', link: '/api/' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/pesitwizard/pesitwizard-rs' }
    ],

    footer: {
      message: 'PeSIT Wizard — open source under Apache-2.0',
      copyright: 'Copyright © 2026'
    },

    search: {
      provider: 'local'
    }
  }
})
