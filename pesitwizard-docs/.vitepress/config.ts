import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'PeSIT Wizard',
  description: 'Modern open-source PeSIT file transfer solution',
  lang: 'en-US',

  // Ignore localhost links used in development examples
  ignoreDeadLinks: [
    /^http:\/\/localhost/,
    /^\/guide\/server\/clustering/
  ],

  head: [
    ['link', { rel: 'icon', href: '/favicon.ico' }]
  ],

  themeConfig: {
    logo: '/logo.svg',

    nav: [
      { text: 'Home', link: '/' },
      { text: 'Guide', link: '/guide/' },
      { text: 'API', link: '/api/' },
      { text: 'GitHub', link: 'https://github.com/pesitwizard/pesitwizard' }
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Introduction',
          items: [
            { text: 'What is PeSIT?', link: '/guide/' },
            { text: 'Quick Start', link: '/guide/quickstart' },
            { text: 'Architecture', link: '/guide/architecture' }
          ]
        },
        {
          text: 'PeSIT Wizard Client',
          items: [
            { text: 'Installation', link: '/guide/client/installation' },
            { text: 'Configuration', link: '/guide/client/configuration' },
            { text: 'Usage', link: '/guide/client/usage' },
            { text: 'ERP Integration', link: '/guide/client/erp-integration' }
          ]
        },
        {
          text: 'PeSIT Wizard Server',
          items: [
            { text: 'Installation', link: '/guide/server/installation' },
            { text: 'Configuration', link: '/guide/server/configuration' },
            { text: 'Security', link: '/guide/server/security' },
            { text: 'Secrets Management', link: '/guide/server/secrets' },
            { text: 'Storage Connectors', link: '/guide/server/connectors' },
            { text: 'Observability', link: '/guide/server/observability' }
          ]
        },
        {
          text: 'Operations',
          items: [
            { text: 'Troubleshooting', link: '/guide/troubleshooting' },
            { text: 'Operations Runbook', link: '/guide/operations' },
            { text: 'Performance', link: '/guide/performance' },
            { text: 'Connect:Express', link: '/guide/connect-express' }
          ]
        }
      ],
      '/api/': [
        {
          text: 'API Reference',
          items: [
            { text: 'Overview', link: '/api/' },
            { text: 'Authentication', link: '/api/authentication' },
            { text: 'Client API', link: '/api/client' },
            { text: 'Server API', link: '/api/server' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/pesitwizard/pesitwizard' }
    ],

    footer: {
      message: 'PeSIT Wizard - Modern PeSIT solution for enterprises',
      copyright: 'Copyright © 2025'
    },

    search: {
      provider: 'local'
    }
  }
})
