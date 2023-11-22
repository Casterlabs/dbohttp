<script lang="ts">
	import SqlEditor from '$lib/SqlEditor/index.svelte';
	import Modal from '$lib/layout/Modal.svelte';
	import { onMount } from 'svelte';

	let list: { query: string; meta: string; rows: any[] }[] = [];
	let connectionModalVisible = false;

	let connectionUrl = '';
	let connectionPassword = '';

	list.push({
		query: "SELECT * FROM test WHERE id = 123 AND active = true AND name = 'John';",
		meta: '{\n  "profile": {\n    "Access Lock Acquisition": 0.00728,\n    "Statement Preparation": 0.476203,\n    "Statement Execution": 0.141481,\n    "Result Gathering": 0.02228,\n    "Result Marshalling": 5.790833,\n    "Database Commit": 0.04904,\n    "Statement Cleanup": 0.00988,\n    "Miscellaneous": 0.12472100000000097\n  },\n  "rowsReturned": 122,\n  "took": 6.621718\n}',
		rows: [
			{
				id: 123,
				active: true,
				name: 'John',
				occupation: undefined,
				profilePicture: [],
				address: null
			}
		]
	});

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

	onMount(() => {
		connectionUrl = localStorage.getItem('casterlabs:dbohttp:url') || '';
		connectionPassword = localStorage.getItem('casterlabs:dbohttp:password') || '';

		if (connectionUrl.length == 0 || connectionPassword.length == 0) {
			connectionModalVisible = true;
		}
	});
</script>

{#if connectionModalVisible}
	<!-- svelte-ignore a11y-label-has-associated-control -->
	<Modal on:close={() => (connectionModalVisible = false)}>
		<span slot="title">Settings</span>
		<div class="pt-2 space-y-4">
			<div>
				<label>Server URL:</label>
				<input
					class="px-1.5 py-1 block w-full text-base-12 rounded-md border transition hover:border-base-8 border-base-7 bg-base-1 shadow-sm focus:border-primary-7 focus:outline-none focus:ring-1 focus:ring-primary-7 text-sm"
					placeholder="https://example.com:10243"
					bind:value={connectionUrl}
					on:change={() => {
						localStorage.setItem('casterlabs:dbohttp:url', connectionUrl);
					}}
				/>
			</div>

			<div>
				<label>Token:</label>
				<input
					type="password"
					class="px-1.5 py-1 block w-full text-base-12 rounded-md border transition hover:border-base-8 border-base-7 bg-base-1 shadow-sm focus:border-primary-7 focus:outline-none focus:ring-1 focus:ring-primary-7 text-sm"
					bind:value={connectionPassword}
					on:change={() => {
						localStorage.setItem('casterlabs:dbohttp:password', connectionPassword);
					}}
				/>
			</div>
		</div>
	</Modal>
{/if}

<div class="flex flex-col h-full">
	<div class="flex-1 relative">
		<ul class="absolute inset-x-0 bottom-2 max-h-full overflow-auto">
			{#each list as result}
				{@const columnNames = getAllColumnNames(result.rows)}
				<li class="my-2">
					<div>
						<icon
							class="inline-block align-middle h-5 w-5 -translate-y-px"
							data-icon="icon/arrow-long-right"
						/>
						<span class="ml-1.5 font-mono">{result.query}</span>

						<button class="ml-2 text-xs underline text-base-11" on:click={() => alert(result.meta)}>
							Debug
						</button>
					</div>

					<div
						class="overflow-hidden text-base-12 rounded-md border border-base-6 bg-base-2 shadow-sm text-sm align-bottom"
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
	<div class="flex-0">
		<SqlEditor />
	</div>
</div>

<button
	class="absolute translate-y-0.5 top-4 right-12 text-base-11"
	on:click={() => (connectionModalVisible = true)}
>
	<icon class="w-5 h-5" data-icon="icon/cog" />
</button>
