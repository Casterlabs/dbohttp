<script lang="ts">
	import { createEventDispatcher } from 'svelte';

	const dispatch = createEventDispatcher();

	export let rows: any[];
	export let allowEditing = false;

	let rowBeingEdited = -1;
	let colBeingEdited = '';
	let newColumnValue: any = null;

	let addingRow = false;
	let newRowData: { [key: string]: string } = {};

	$: columnNames = getAllColumnNames(rows || []);

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
</script>

{#if rows}
	<div
		class="overflow-hidden text-base-12 rounded-md border border-base-6 bg-base-2 shadow-sm text-sm align-bottom"
		style="overflow-x: auto;"
	>
		<table class="relative min-w-full" class:mb-8={!addingRow && allowEditing}>
			<thead class="bg-base-6">
				<tr>
					{#each columnNames as name}
						<th scope="col" class="py-3.5 px-3 text-left text-sm font-semibold text-base-12">
							{name}
						</th>
					{/each}
					{#if allowEditing}
						<th scope="col" class="opacity-0 px-2">
							Actions <span aria-hidden="true">spacer</span>
						</th>
					{/if}
				</tr>
			</thead>
			<tbody>
				{#each rows as row, rowIdx}
					<tr class="border-t border-base-6">
						{#each columnNames as colName}
							{@const value = row[colName]}
							<td class="whitespace-nowrap px-3 py-4 text-sm text-base-10 font-mono relative pr-7">
								{#if rowBeingEdited == rowIdx && colBeingEdited == colName}
									<input
										placeholder="Must be valid JSON"
										class="w-full rounded bg-base-4 text-base-12 border border-base-6 px-1"
										bind:value={newColumnValue}
									/>
									<button
										title="Save"
										class="absolute inset-y-0 right-0 flex items-center justify-center text-base-12"
										on:click={() => {
											const data = {
												existingData: row,
												newValue: {
													col: colName,
													val: JSON.parse(newColumnValue)
												}
											};
											dispatch('edit', data);

											rowBeingEdited = -1;
											colBeingEdited = '';
											newColumnValue = null;
										}}
									>
										<icon class="w-5 h-5" data-icon="check" />
									</button>
								{:else}
									{#if value === undefined}
										<span title="null">No data</span>
									{:else if value === null}
										<span title="null">Null</span>
									{:else if value instanceof Array}
										<span title="blob">Blob</span>
									{:else}
										<span class="text-base-12" title={typeof value}>{value}</span>
									{/if}
									{#if allowEditing}
										<button
											title="Edit"
											disabled={rowBeingEdited != -1}
											class="absolute inset-y-0 right-0 flex items-center justify-center"
											on:click={() => {
												rowBeingEdited = rowIdx;
												colBeingEdited = colName;
												newColumnValue = JSON.stringify(row[colName]);
											}}
										>
											<icon class="w-5 h-5" data-icon="pencil-square" />
										</button>
									{/if}
								{/if}
							</td>
						{/each}
						{#if allowEditing}
							<td class="px-2 text-right">
								<button
									title="Duplicate"
									class="inline-block"
									on:click={() => {
										newRowData = {};
										for (const [key, value] of Object.entries(row)) {
											newRowData[key] = JSON.stringify(value);
										}
										addingRow = true;
									}}
								>
									<icon class="w-5 h-5" data-icon="document-duplicate" />
								</button>
								<button
									title="Save"
									class="inline-block text-red-400"
									on:click={() => {
										dispatch('delete-row', row);
									}}
								>
									<icon class="w-5 h-5" data-icon="trash" />
								</button>
							</td>
						{/if}
					</tr>
				{/each}
				{#if addingRow}
					<tr class="border-t border-base-6">
						{#each columnNames as colName}
							<td class="whitespace-nowrap px-3 py-4 text-sm text-base-10 font-mono">
								<input
									placeholder="Must be valid JSON"
									class="w-full rounded bg-base-4 text-base-12 border border-base-6 px-1"
									bind:value={newRowData[colName]}
								/>
							</td>
						{/each}
						<td class="px-2 text-base-12 text-right">
							<button
								class="inline-block"
								on:click={() => {
									dispatch('add-row', newRowData);
									addingRow = false;
								}}
							>
								<icon class="w-5 h-5" data-icon="check" />
							</button>
							<button
								class="inline-block"
								on:click={() => {
									addingRow = false;
								}}
							>
								<icon class="w-5 h-5" data-icon="x-mark" />
							</button>
						</td>
					</tr>
				{/if}
			</tbody>
			{#if !addingRow && allowEditing}
				<button
					disabled={rowBeingEdited != -1}
					title="Add row"
					class="absolute -bottom-8 inset-x-0 border-t border-base-6 w-full py-2 hover:bg-base-4 flex items-center justify-center"
					on:click={() => {
						addingRow = true;
					}}
				>
					<icon class="w-5 h-5" data-icon="plus" />
				</button>
			{/if}
		</table>
	</div>
{/if}
