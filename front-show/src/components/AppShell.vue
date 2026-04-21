<template>
  <div class="app-shell">
    <aside class="sidebar">
      <slot name="sidebar" />
    </aside>
    <main class="workspace">
      <slot />
    </main>
  </div>
</template>

<style scoped lang="scss">
.app-shell {
  display: grid;
  grid-template-columns: minmax(250px, 300px) minmax(0, 1fr);
  gap: 14px;
  min-height: calc(100vh - 32px);
  padding: 16px;
}

.sidebar,
.workspace {
  border: 1px solid var(--line-color);
  border-radius: 8px;
  background: var(--panel-bg);
  overflow: hidden;
  min-height: 0;
}

@media (max-width: 900px) {
  .app-shell {
    grid-template-columns: 1fr;
    grid-template-rows: auto 1fr;
    min-height: calc(100vh - 20px);
    padding: 10px;
    gap: 10px;
  }

  .sidebar {
    max-height: 40vh;
  }
}
</style>
