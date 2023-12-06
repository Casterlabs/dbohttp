<script lang="ts">
	import { connectionUrl, connectionPassword } from '$lib/app/stores';
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { goto } from '$app/navigation';

	let sidebarItems = [
		{
			icon: 'cube',
			name: 'Explorer',
			href: '/app/explorer'
		},
		{
			icon: 'pencil-square',
			name: 'Insert',
			href: '/app/insert'
		},
		{
			icon: 'command-line',
			name: 'Console',
			href: '/app/console'
		},
		{
			icon: 'cog',
			name: 'Settings',
			href: '/app/settings'
		}
	];

	onMount(() => {
		const search = new URLSearchParams(location.search);
		if (search.has('url')) {
			connectionUrl.set(search.get('url') as string);
		}
		if (search.has('password')) {
			connectionPassword.set(search.get('password') as string);
		}

		if ($connectionUrl.length == 0 || $connectionPassword.length == 0) {
			goto('/app/settings');
		}
	});
</script>

<div class="flex flex-row w-full h-full">
	<div class="border-r border-base-4 bg-base-2 w-fit h-full p-4">
		<ul class="space-y-4">
			{#each sidebarItems as { icon, name, href }}
				{@const isSelected = $page.url.pathname == href}
				<li title={name}>
					<a
						{href}
						class="block p-4 border border-base-4 bg-base-2 hover:bg-base-5 hover:border-base-7 rounded"
						class:bg-base-6={isSelected}
						class:border-base-7={isSelected}
					>
						<icon data-icon={icon} />
					</a>
				</li>
			{/each}
		</ul>
	</div>
	<div class="flex-1 overflow-auto p-2 pl-4">
		<slot />
	</div>
</div>

<style>
	:global(#dark-light-toggle) {
		top: unset !important;
		right: unset !important;
		bottom: 2.25rem;
		left: 2.25rem;
	}
</style>
