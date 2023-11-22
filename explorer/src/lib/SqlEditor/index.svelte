<script lang="ts">
	import createConsole from '../consoleHelper';
	import { renderHighlight } from './util';

	const KEYWORDS = {
		'var(--sql-keyword-1)': [
			'SELECT',
			'FROM',
			'WHERE',
			'LIKE',
			'BETWEEN',
			'NOT',
			'FROM',
			'IN',
			'AND',
			'OR'
		],
		'var(--sql-keyword-2)': ['NULL', 'TRUE', 'FALSE'],
		'var(--sql-keyword-3)': /^[0-9]+$/
	};
	const console = createConsole('SQLEditor');

	let virtualInput: HTMLDivElement;
	export let input = 'SELECT * FROM test WHERE id = 123 AND active = true AND name = "John";';

	$: input,
		(() => {
			if (!virtualInput) return;
			if (virtualInput.innerText != input) {
				virtualInput.innerText = input;
			}
			renderHighlight(virtualInput, KEYWORDS);
		})();
</script>

<!-- svelte-ignore a11y-no-abstract-role -->
<div
	role="input"
	contenteditable="true"
	class="resize-y h-24 px-1.5 py-1 block w-full text-base-12 rounded-md border transition hover:border-base-8 border-base-7 bg-base-1 shadow-sm focus:border-primary-7 focus:outline-none focus:ring-1 focus:ring-primary-7 text-sm"
	bind:this={virtualInput}
	on:keyup={(e) => {
		if (
			e.ctrlKey ||
			['Shift', 'Control', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)
		) {
			return; // Don't trigger an update.
		}

		input = virtualInput.innerText;
	}}
/>

<style>
	:global(#css-intermediate) {
		--sql-keyword-1: #db7103;
		--sql-keyword-2: #1010e5;
		--sql-keyword-3: #128f12;
	}

	:global(#css-intermediate.dark-theme) {
		--sql-keyword-1: #e9cf04;
		--sql-keyword-2: #8484ff;
		--sql-keyword-3: #3cdb3c;
	}
</style>
