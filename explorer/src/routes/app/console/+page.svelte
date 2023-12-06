<script lang="ts">
	import SQLEditor from '$lib/app/SQLEditor.svelte';
	import { connectionUrl, connectionPassword, checkSettings } from '$lib/app/stores';
	import { onMount } from 'svelte';

	let list: { query: string; meta: string; rows: any[] }[] = [];

	let currentQuery = '';
	let lastQuery = '';
	let executingQuery = false;

	function getAllColumnNames(rows: any[][]) {
		return rows
			.map((r) => Object.keys(r))
			.reduce(
				//
				(s, names) => {
					names.forEach((n) => s.add(n));
					return s;
				},
				new Set<string>()
			);
	}

	function executeQuery() {
		executingQuery = true;
		document.body.focus();
		fetch($connectionUrl, {
			method: 'POST',
			headers: new Headers({
				Authorization: 'Bearer ' + $connectionPassword,
				'Content-Type': 'text/plain'
			}),
			body: currentQuery
		})
			.then((response) => response.json())
			.then((json) => {
				if (json.error) throw `[${json.error.code}] ${json.error.message}`;
				list.push({
					query: currentQuery,
					meta: JSON.stringify(json.meta, null, 2),
					rows: json.results
				});
				list = list;

				lastQuery = currentQuery;
				localStorage.setItem('casterlabs:dbohttp:last_query', lastQuery);
				currentQuery = '';
			})
			.catch(alert)
			.finally(() => (executingQuery = false));
	}

	onMount(() => {
		checkSettings();
		lastQuery = localStorage.getItem('casterlabs:dbohttp:last_query') || '';
	});
</script>

<div class="flex flex-col h-full">
	<div class="flex-1 relative">
		<ul class="absolute inset-x-0 bottom-2 max-h-full overflow-auto">
			{#each list as result}
				{@const columnNames = getAllColumnNames(result.rows)}
				<li class="my-2">
					<div>
						<icon
							class="inline-block align-middle h-5 w-5 -translate-y-px"
							data-icon="arrow-long-right"
						/>
						<span class="ml-1.5 font-mono">{result.query}</span>

						<button class="ml-2 text-xs underline text-base-11" on:click={() => alert(result.meta)}>
							Debug
						</button>
					</div>

					<div
						class="overflow-hidden text-base-12 rounded-md border border-base-6 bg-base-2 shadow-sm text-sm align-bottom"
						style="overflow-x: auto;"
					>
						<table class="min-w-full">
							<thead class="bg-base-6">
								<tr>
									{#each columnNames as name}
										<th
											scope="col"
											class="py-3.5 px-3 text-left text-sm font-semibold text-base-12"
										>
											{name}
										</th>
									{/each}
								</tr>
							</thead>
							<tbody>
								{#each result.rows as row}
									<tr class="border-t border-base-6">
										{#each columnNames as name}
											{@const value = row[name]}
											<td class="whitespace-nowrap px-3 py-4 text-sm text-base-10 font-mono">
												{#if value === undefined}
													No data
												{:else if value === null}
													<span title="null">Null</span>
												{:else if value instanceof Array}
													<span title="blob">Blob</span>
												{:else}
													<span class="text-base-12" title={typeof value}>{value}</span>
												{/if}
											</td>
										{/each}
									</tr>
								{/each}
							</tbody>
						</table>
					</div>
				</li>
			{/each}
		</ul>
	</div>
	<!-- svelte-ignore a11y-no-static-element-interactions -->
	<div
		class="flex-0 relative"
		class:opacity-60={executingQuery}
		on:keyup={(e) => {
			if (e.ctrlKey && e.key == 'ArrowUp') {
				currentQuery = lastQuery;
			}
			if (e.key == 'Enter') {
				if (currentQuery.trim().endsWith(';')) {
					executeQuery();
				}
			}
		}}
	>
		<SQLEditor bind:input={currentQuery} />
		<button class="absolute bottom-1 right-1.5" on:click={executeQuery}> Execute </button>
		{#if executingQuery}
			<div class="absolute inset-0" />
		{/if}
	</div>
</div>
