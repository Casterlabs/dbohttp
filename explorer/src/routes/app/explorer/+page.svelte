<script lang="ts">
	import Rows from '$lib/app/Rows.svelte';

	import { connectionUrl, connectionPassword, checkSettings } from '$lib/app/stores';
	import { onMount } from 'svelte';

	const QUERY_LIMIT = 20;

	let tables: string[] = [];
	let rows: any[] = [];
	let debugMeta = {};
	let executingQuery = false;

	let currentTable: string | null = null;
	let offset = 0;
	let total = 0;

	async function loadData() {
		executingQuery = true;
		try {
			await fetch($connectionUrl, {
				method: 'POST',
				headers: new Headers({
					Authorization: 'Bearer ' + $connectionPassword,
					'Content-Type': 'text/plain'
				}),
				body: `SELECT COUNT(*) FROM '${currentTable}';`
			})
				.then((response) => response.json())
				.then((json) => {
					if (json.error) throw `[${json.error.code}] ${json.error.message}`;
					total = json.results[0]['COUNT(*)'];
				});
			await fetch($connectionUrl, {
				method: 'POST',
				headers: new Headers({
					Authorization: 'Bearer ' + $connectionPassword,
					'Content-Type': 'text/plain'
				}),
				body: `SELECT * FROM '${currentTable}' LIMIT ${QUERY_LIMIT} OFFSET ${offset};`
			})
				.then((response) => response.json())
				.then((json) => {
					if (json.error) throw `[${json.error.code}] ${json.error.message}`;
					rows = json.results;
					debugMeta = json.meta;
				});
		} catch (e) {
			alert(e);
		} finally {
			executingQuery = false;
		}
	}

	function switchTable(newTable: string) {
		currentTable = newTable;
		offset = 0;
		total = 0;
		loadData();
	}

	onMount(() => {
		checkSettings();

		executingQuery = true;
		fetch($connectionUrl, {
			method: 'GET',
			headers: new Headers({
				Authorization: 'Bearer ' + $connectionPassword
			})
		})
			.then((response) => response.json())
			.then((json) => {
				if (json.error) throw `[${json.error.code}] ${json.error.message}`;
				tables = json.info.tables;
				switchTable(tables[0]);
			})
			.catch(alert)
			.finally(() => (executingQuery = false));
	});
</script>

<div class="-mx-4 px-2 mt-3 mb-4 border-b border-base-8">
	<nav class="-mb-1.5 flex space-x-4 w-full overflow-auto hide-scrollbar">
		{#each tables as table}
			{@const isSelected = currentTable == table}
			<button
				class="border-current whitespace-nowrap pb-4 font-medium text-sm"
				aria-current={isSelected ? 'page' : undefined}
				class:border-b-2={isSelected}
				class:text-primary-11={isSelected}
				on:click={() => switchTable(table)}
			>
				{table}
			</button>
		{/each}
	</nav>
</div>

{#if rows.length > 1}
	<Rows {rows} />

	<div class="mt-2 mb-12 text-center text-base-11">
		<p class="text-sm">
			Showing results {offset}-{Math.min(offset + QUERY_LIMIT, total)} of {total}.
			<button class="text-xs underline" on:click={() => alert(JSON.stringify(debugMeta, null, 2))}>
				Debug
			</button>
		</p>
		<button
			class:text-base-9={offset == 0}
			disabled={offset == 0}
			on:click={() => {
				offset -= QUERY_LIMIT;
				loadData();
			}}
		>
			Previous
		</button>
		&bull;
		<button
			class:text-base-9={offset + QUERY_LIMIT > total}
			disabled={offset + QUERY_LIMIT > total}
			on:click={() => {
				offset += QUERY_LIMIT;
				loadData();
			}}
		>
			Next
		</button>
	</div>
{:else}
	<p class="mt-2 mb-12 text-center text-base-11 text-sm">Empty.</p>
{/if}
