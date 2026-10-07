<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { computed, ref, watch } from "vue";
const props = defineProps({
  rows: { type: Array, default: () => [] },
  columns: { type: Array, default: () => [] },
  lang: { type: String, default: "zh" },
  empty: { type: String, default: "" },
  search: { type: Boolean, default: true },
});
const term = ref(""),
  page = ref(1),
  sort = ref(""),
  descending = ref(false);
const t = (zh, en) => (props.lang === "zh" ? zh : en);
const filtered = computed(() => {
  const needle = term.value.toLocaleLowerCase();
  let rows = props.rows.filter(
    (row) =>
      !needle ||
      props.columns.some((c) =>
        String(row[c.key] ?? "")
          .toLocaleLowerCase()
          .includes(needle),
      ),
  );
  if (sort.value)
    rows = [...rows].sort((a, b) => {
      const x = a[sort.value],
        y = b[sort.value];
      const v =
        typeof x === "number" && typeof y === "number"
          ? x - y
          : String(x ?? "").localeCompare(
              String(y ?? ""),
              props.lang === "zh" ? "zh-CN" : "en",
            );
      return descending.value ? -v : v;
    });
  return rows;
});
const totalPages = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / 10)),
);
const visible = computed(() =>
  filtered.value.slice((page.value - 1) * 10, page.value * 10),
);
watch([term, sort, () => props.rows], () => {
  page.value = 1;
});
function sortBy(key) {
  if (sort.value === key) descending.value = !descending.value;
  else {
    sort.value = key;
    descending.value = false;
  }
}
</script>
<template>
  <div class="data-panel">
    <div v-if="search" class="table-tools">
      <input
        v-model="term"
        type="search"
        :aria-label="t('搜索当前列表', 'Search current list')"
        :placeholder="
          t('搜索名称、编号或状态', 'Search name, reference or status')
        "
      /><span>{{ filtered.length }} {{ t("条记录", "records") }}</span
      ><slot name="tools"></slot>
    </div>
    <div class="table-scroll">
      <table>
        <thead>
          <tr>
            <th v-for="col in columns" :key="col.key">
              <button
                type="button"
                class="sort-button"
                @click="sortBy(col.key)"
              >
                {{ col.label
                }}<span v-if="sort === col.key">{{
                  descending ? " ↓" : " ↑"
                }}</span>
              </button>
            </th>
            <th v-if="$slots.actions" class="actions-head">
              {{ t("操作", "Actions") }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in visible" :key="row.id ?? row.reference">
            <td v-for="col in columns" :key="col.key" :data-label="col.label">
              <slot name="cell" :row="row" :col="col">{{ row[col.key] }}</slot>
            </td>
            <td v-if="$slots.actions" class="row-actions">
              <slot name="actions" :row="row"></slot>
            </td>
          </tr>
          <tr v-if="!visible.length">
            <td
              :colspan="columns.length + ($slots.actions ? 1 : 0)"
              class="empty-cell"
            >
              {{ empty || t("暂无记录", "No records yet") }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pagination">
      <span
        >{{ t("每页10条", "10 per page") }} · {{ page }} /
        {{ totalPages }}</span
      ><button
        type="button"
        :disabled="page <= 1"
        :aria-label="t('上一页', 'Previous page')"
        @click="page--"
      >
        ←</button
      ><button
        type="button"
        :disabled="page >= totalPages"
        :aria-label="t('下一页', 'Next page')"
        @click="page++"
      >
        →
      </button>
    </div>
  </div>
</template>
