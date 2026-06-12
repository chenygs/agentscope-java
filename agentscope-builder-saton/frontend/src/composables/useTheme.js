import { computed, ref } from 'vue';
import { darkTheme } from 'naive-ui';
const isDark = ref(localStorage.getItem('theme') !== 'light');
export function useTheme() {
    const theme = computed(() => (isDark.value ? darkTheme : null));
    const themeOverrides = computed(() => ({
        common: {
            primaryColor: '#f97316',
            primaryColorHover: '#fb923c',
            primaryColorPressed: '#ea580c',
            bodyColor: isDark.value ? '#0a0a0a' : '#fafafa',
            cardColor: isDark.value ? '#18181b' : '#ffffff',
            modalColor: isDark.value ? '#18181b' : '#ffffff',
            popoverColor: isDark.value ? '#18181b' : '#ffffff',
            borderColor: isDark.value ? '#27272a' : '#e4e4e7',
            textColor1: isDark.value ? '#fafafa' : '#18181b',
            textColor2: isDark.value ? '#a1a1aa' : '#71717a',
            tableHeaderColor: isDark.value ? '#202022' : '#f4f4f5',
            inputColor: isDark.value ? '#202022' : '#f4f4f5',
            hoverColor: isDark.value ? '#202022' : '#f4f4f5',
        },
    }));
    function toggleTheme() {
        isDark.value = !isDark.value;
        localStorage.setItem('theme', isDark.value ? 'dark' : 'light');
    }
    return { isDark, theme, themeOverrides, toggleTheme };
}
