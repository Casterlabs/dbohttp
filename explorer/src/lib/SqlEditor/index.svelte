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
			'IN',
			'LIMIT',
			'AND',
			'OR',
			'ORDER',
			'BY',
			'IS'
		],
		'var(--sql-keyword-2)': ['NULL', 'TRUE', 'FALSE'],
		'var(--sql-keyword-3)': /^[0-9]+$/
	};
	const console = createConsole('SQLEditor');

	let virtualInput: HTMLDivElement;
	export let input = '';

	$: input,
		(() => {
			if (!virtualInput) return;
			if (virtualInput.innerText.trim().replaceAll(/\u00a0/g, ' ') != input.trim()) {
				virtualInput.innerText = input;
			}
			renderHighlight(virtualInput, KEYWORDS);
		})();
</script>

<div class="relative">
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

			input = virtualInput.innerText.replaceAll(/\u00a0/g, ' ');
		}}
	/>

	{#if input.length == 0}
		<span class="absolute top-1.5 left-2 text-base-7 text-sm"> SELECT * FROM ... </span>
	{/if}
</div>

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
