import { QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, RouterProvider } from 'react-router';
import type { SkulletTheme } from 'skullet';
import { SkulletProvider } from 'skullet/react';
import { queryClient } from '@/lib/queryClient';
import { routes } from '@/routes';
import '@/state/glass';
import 'skullet/styles.css';
import '@/styles/global.css';

export const skulletTheme: SkulletTheme = {
  light: {
    base: 'rgba(0, 0, 0, 0.07)',
    highlight: 'rgba(255, 255, 255, 0.6)',
  },
  dark: {
    base: 'rgba(255, 255, 255, 0.08)',
    highlight: 'rgba(255, 255, 255, 0.1)',
  },
  animation: 'shimmer',
  speed: 'slow',
  radius: '6px',
  text: {
    top: 'ascender',
    bottom: 'descender',
  },
};

const router = createBrowserRouter(routes);

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <SkulletProvider theme={skulletTheme} mode="system">
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </SkulletProvider>
  </StrictMode>,
);
