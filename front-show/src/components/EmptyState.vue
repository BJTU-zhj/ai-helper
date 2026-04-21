<template>
  <div class="empty-state">
    <p>开始一段新的对话</p>
    <span>你可以提问烹饪、营养或任何日常问题</span>
  </div>
</template>

<style scoped lang="scss">
.empty-state {
  min-height: 220px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  color: var(--text-muted);
  text-align: center;
  padding: 24px;
}

p {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
}

span {
  font-size: 13px;
  line-height: 1.6;
}
</style>
