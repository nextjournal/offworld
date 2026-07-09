import { defineConfig } from 'vite';
import squint from 'squint-cljs/vite';

export default defineConfig(() => {
  return {
    root: 'resources/public',
    plugins: [squint()],
  };
});
