const iconCache: { [key: string]: string } = {};

function replaceIcon(element: Element) {
	if (element.hasAttribute('data-replaced')) return;

	const iconPath = element.getAttribute('data-icon') as string;
	element.setAttribute('data-replaced', 'true');

	if (iconCache[iconPath]) {
		element.innerHTML = iconCache[iconPath];
		console.debug('[Icons]', 'Loaded icon from cache:', iconPath);
	} else {
		(async () => {
			try {
				const svg = await fetch(`/icons/${iconPath}.svg`) //
					.then((res) => {
						if (res.ok) {
							return res.text();
						} else {
							throw 'Status: ' + res.status;
						}
					});

				element.innerHTML = iconCache[iconPath] = svg;
				console.debug('[Icons]', 'Loaded icon:', iconPath);
			} catch (e) {
				element.innerHTML = iconCache[iconPath] =
					'<div class="bg-red-500 h-full w-full text-white" title="MISSING ICON">X</div>'; // Visual error.
				console.error('[Icons]', 'Could not load icon', iconPath, 'due to an error:');
				console.error(e);
			}
		})();
	}
}

export default function hook() {
	new MutationObserver((records) => {
		for (const record of records) {
			if (record.addedNodes.length > 0) {
				for (const element of record.addedNodes) {
					if (element.nodeName.toLowerCase() == 'icon') {
						replaceIcon(element as Element);
						break;
					}
				}
			}

			// SvelteKit likes to undo all of our hard work. Let's prevent that.
			const element = record.target;
			if (element.nodeName.toLowerCase() == 'icon') {
				replaceIcon(element as Element);
			}
		}
	}).observe(document.body, {
		subtree: true,
		attributes: true,
		childList: true
	});

	document.querySelectorAll('icon').forEach(replaceIcon);
}
