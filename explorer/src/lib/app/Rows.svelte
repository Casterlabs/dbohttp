<script lang="ts">
	export let rows: any[];

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
		<table class="min-w-full">
			<thead class="bg-base-6">
				<tr>
					{#each columnNames as name}
						<th scope="col" class="py-3.5 px-3 text-left text-sm font-semibold text-base-12">
							{name}
						</th>
					{/each}
				</tr>
			</thead>
			<tbody>
				{#each rows as row}
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
{/if}
